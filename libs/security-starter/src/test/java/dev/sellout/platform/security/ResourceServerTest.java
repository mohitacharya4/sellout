package dev.sellout.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "sellout.oidc.issuer=http://localhost:8180/realms/sellout",
      "sellout.oidc.audience=sellout-api",
      "management.endpoints.web.exposure.include=health,info,prometheus"
    })
class ResourceServerTest {

  @Autowired private Environment environment;
  @MockitoBean private JwtDecoder jwtDecoder;
  private final RestClient client = RestClient.create();

  @BeforeEach
  void stubDecoder() {
    doThrow(new JwtException("bad token")).when(jwtDecoder).decode(anyString());
    doReturn(token(List.of("CUSTOMER"))).when(jwtDecoder).decode("customer-token");
    doReturn(token(List.of("ADMIN"))).when(jwtDecoder).decode("admin-token");
  }

  @Test
  void permitsHealthWithoutAToken() {
    assertThat(status("/healthz", null)).isEqualTo(200);
  }

  @Test
  void actuatorInfoRequiresAToken() {
    assertThat(status("/actuator/info", null)).isEqualTo(401);
  }

  @Test
  void rejectsMissingAndInvalidTokens() {
    assertThat(status("/ping", null)).isEqualTo(401);
    assertThat(status("/ping", "garbage")).isEqualTo(401);
  }

  @Test
  void acceptsAValidTokenAndEnforcesRoles() {
    assertThat(status("/ping", "customer-token")).isEqualTo(200);
    assertThat(status("/admin", "customer-token")).isEqualTo(403);
    assertThat(status("/admin", "admin-token")).isEqualTo(200);
  }

  private int status(String path, String bearer) {
    RestClient.RequestHeadersSpec<?> spec =
        client
            .get()
            .uri("http://localhost:" + environment.getRequiredProperty("local.server.port") + path);
    if (bearer != null) {
      spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
    }
    return spec.exchange((request, response) -> response.getStatusCode().value());
  }

  private static Jwt token(List<String> roles) {
    return Jwt.withTokenValue("t")
        .header("alg", "none")
        .subject("alice")
        .issuer("http://localhost:8180/realms/sellout")
        .audience(List.of("sellout-api"))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .claim("realm_access", Map.of("roles", roles))
        .build();
  }
}
