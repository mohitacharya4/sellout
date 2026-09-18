package dev.sellout.testing.containers;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared, reusable containers for integration tests. Import with
 * {@code @ImportTestcontainers(SelloutContainers.class)} (or {@link IntegrationTest}). Redis and
 * Kafka join in the slices that first need them.
 */
public interface SelloutContainers {

  String POSTGRES_IMAGE = "postgres:18-alpine";
  String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.7";
  String REALM = "sellout";
  String AUDIENCE = "sellout-api";

  @ServiceConnection
  PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE).withReuse(true);

  KeycloakContainer KEYCLOAK =
      new KeycloakContainer(KEYCLOAK_IMAGE)
          .withRealmImportFile("/keycloak/realm-sellout.json")
          .withReuse(true);

  @DynamicPropertySource
  static void oidcProperties(DynamicPropertyRegistry registry) {
    registry.add("sellout.oidc.issuer", () -> KEYCLOAK.getIssuerUrl(REALM));
    registry.add("sellout.oidc.audience", () -> AUDIENCE);
  }
}
