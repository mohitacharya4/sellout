package dev.sellout.testing.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sellout.platform.security.KeycloakGrantedAuthoritiesConverter;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class JwtFixturesTest {

  @Test
  void customerTokenCarriesTheCustomerRealmRoleAndAudience() {
    var jwt = JwtFixtures.customer();

    assertThat(jwt.getSubject()).isEqualTo("alice");
    assertThat(jwt.getAudience()).containsExactly(JwtFixtures.AUDIENCE);
    assertThat(jwt.getIssuer().toString()).isEqualTo(JwtFixtures.ISSUER);
    assertThat(new KeycloakGrantedAuthoritiesConverter().convert(jwt))
        .extracting(GrantedAuthority::getAuthority)
        .contains("ROLE_CUSTOMER");
  }

  @Test
  void eachFixtureHasExactlyItsRole() {
    var converter = new KeycloakGrantedAuthoritiesConverter();

    assertThat(converter.convert(JwtFixtures.organizer()))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_ORGANIZER");
    assertThat(converter.convert(JwtFixtures.admin()))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_ADMIN");
    assertThat(converter.convert(JwtFixtures.noRoles())).isEmpty();
  }
}
