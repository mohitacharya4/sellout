package dev.sellout.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.sellout.testing.containers.IntegrationTest;
import dev.sellout.testing.containers.SelloutContainers;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

@IntegrationTest
@ActiveProfiles("demo")
@TestPropertySource(properties = "management.tracing.enabled=false")
class InventoryApplicationIT {

  private static final String DEMO_EVENT = "11111111-1111-1111-1111-111111111111";

  // Boot's OAuth2ResourceServerAutoConfiguration gates its JwtDecoder bean on IssuerUriCondition,
  // which is evaluated while bean definitions are being registered -- before dynamic property
  // registration on SelloutContainers (picked up via @ImportTestcontainers) has run, and before
  // Keycloak's mapped port exists. Starting the container up front, and registering the concrete
  // Spring Security property names directly (rather than through the sellout.oidc.issuer ->
  // issuer-uri bridge, whose nested placeholder isn't resolvable that early either), makes the
  // issuer available in time for that condition. In production these come from
  // SELLOUT_OIDC_ISSUER, a plain environment variable resolved synchronously, so the bridge is
  // unaffected there.
  static {
    SelloutContainers.KEYCLOAK.start();
  }

  @Autowired private Environment environment;
  @Autowired private Flyway flyway;
  private final RestClient client = RestClient.create();

  @DynamicPropertySource
  static void resourceServerProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri",
        () -> SelloutContainers.KEYCLOAK.getIssuerUrl(SelloutContainers.REALM));
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.audiences", () -> SelloutContainers.AUDIENCE);
  }

  @Test
  void flywayAppliedTheSchemaAndJpaValidatedIt() {
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
  }

  @Test
  void aRealKeycloakTokenReadsTheSeededEvent() {
    String token = tokenFor("alice");

    ResponseEntity<String> response =
        client
            .get()
            .uri(url("/events/" + DEMO_EVENT))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .retrieve()
            .toEntity(String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).contains("\"name\":\"Winter Gala\"");
  }

  @Test
  void aTamperedTokenIs401() {
    String token = tokenFor("alice");
    String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

    assertThat(status("/events/" + DEMO_EVENT, tampered)).isEqualTo(401);
  }

  @Test
  void readyzGoesRedWhilePostgresIsPausedAndRecovers() {
    assertThat(status("/readyz", null)).isEqualTo(200);
    String containerId = SelloutContainers.POSTGRES.getContainerId();
    var docker = SelloutContainers.POSTGRES.getDockerClient();

    docker.pauseContainerCmd(containerId).exec();
    try {
      await()
          .atMost(Duration.ofSeconds(20))
          .untilAsserted(() -> assertThat(status("/readyz", null)).isEqualTo(503));
      assertThat(status("/healthz", null)).as("liveness stays up").isEqualTo(200);
    } finally {
      docker.unpauseContainerCmd(containerId).exec();
    }

    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(() -> assertThat(status("/readyz", null)).isEqualTo(200));
  }

  private static String tokenFor(String username) {
    return SelloutContainers.KEYCLOAK.getAccessToken(
        SelloutContainers.REALM, "sellout-ui", username, "password");
  }

  private int status(String path, String bearer) {
    RestClient.RequestHeadersSpec<?> spec = client.get().uri(url(path));
    if (bearer != null) {
      spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
    }
    return spec.exchange((request, response) -> response.getStatusCode().value());
  }

  private String url(String path) {
    return "http://localhost:" + environment.getRequiredProperty("local.server.port") + path;
  }
}
