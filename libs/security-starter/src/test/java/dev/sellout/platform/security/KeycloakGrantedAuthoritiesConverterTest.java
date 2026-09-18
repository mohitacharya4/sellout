package dev.sellout.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakGrantedAuthoritiesConverterTest {

  private final KeycloakGrantedAuthoritiesConverter converter =
      new KeycloakGrantedAuthoritiesConverter();

  @Test
  void mapsRealmRolesToRoleAuthoritiesAndScopesToScopeAuthorities() {
    Jwt jwt =
        jwt()
            .claim("realm_access", Map.of("roles", List.of("CUSTOMER", "offline_access")))
            .claim("scope", "openid inventory:hold")
            .build();

    assertThat(converter.convert(jwt))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder(
            "ROLE_CUSTOMER", "ROLE_offline_access", "SCOPE_openid", "SCOPE_inventory:hold");
  }

  @Test
  void tokenWithoutRealmAccessYieldsOnlyScopes() {
    Jwt jwt = jwt().claim("scope", "openid").build();

    assertThat(converter.convert(jwt))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("SCOPE_openid");
  }

  @Test
  void tokenWithNeitherYieldsNothing() {
    assertThat(converter.convert(jwt().build())).isEmpty();
  }

  @Test
  void realmAccessThatIsNotAnObjectYieldsOnlyScopes() {
    Jwt jwt = jwt().claim("realm_access", "garbage").claim("scope", "openid").build();

    assertThat(converter.convert(jwt))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("SCOPE_openid");
  }

  private static Jwt.Builder jwt() {
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .subject("alice")
        .issuedAt(Instant.EPOCH)
        .expiresAt(Instant.EPOCH.plusSeconds(300));
  }
}
