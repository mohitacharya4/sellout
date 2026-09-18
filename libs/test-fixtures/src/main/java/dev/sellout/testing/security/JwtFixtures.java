package dev.sellout.testing.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import dev.sellout.platform.security.KeycloakGrantedAuthoritiesConverter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Ready-made access tokens shaped like Keycloak's, one per role, for MockMvc and unit tests. */
public final class JwtFixtures {

  public static final String ISSUER = "http://localhost:8180/realms/sellout";
  public static final String AUDIENCE = "sellout-api";

  private JwtFixtures() {}

  public static Jwt customer() {
    return withRoles("alice", "CUSTOMER");
  }

  public static Jwt organizer() {
    return withRoles("oscar", "ORGANIZER");
  }

  public static Jwt admin() {
    return withRoles("admin", "ADMIN");
  }

  public static Jwt noRoles() {
    return withRoles("nobody");
  }

  public static Jwt withRoles(String subject, String... roles) {
    return Jwt.withTokenValue("fixture-" + subject)
        .header("alg", "none")
        .subject(subject)
        .issuer(ISSUER)
        .audience(List.of(AUDIENCE))
        .issuedAt(Instant.EPOCH)
        .expiresAt(Instant.EPOCH.plusSeconds(300))
        .claim("realm_access", Map.of("roles", List.of(roles)))
        .claim("preferred_username", subject)
        .build();
  }

  /**
   * MockMvc post-processor: {@code mockMvc.perform(get("/x").with(JwtFixtures.as(customer())))}.
   */
  public static RequestPostProcessor as(Jwt jwt) {
    return jwt().jwt(jwt).authorities(new KeycloakGrantedAuthoritiesConverter());
  }
}
