package dev.sellout.platform.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Keycloak puts realm roles under {@code realm_access.roles}. This maps them to {@code ROLE_*}
 * authorities (so {@code hasRole('CUSTOMER')} works) and keeps Spring's default {@code SCOPE_*}
 * mapping for client-credentials scopes.
 */
public final class KeycloakGrantedAuthoritiesConverter
    implements Converter<Jwt, Collection<GrantedAuthority>> {

  private static final String REALM_ACCESS = "realm_access";
  private static final String ROLES = "roles";

  private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    List<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
    for (String role : realmRoles(jwt)) {
      authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
    }
    return authorities;
  }

  private static List<String> realmRoles(Jwt jwt) {
    if (!(jwt.getClaims().get(REALM_ACCESS) instanceof Map<?, ?> realmAccess)
        || !(realmAccess.get(ROLES) instanceof List<?> roles)) {
      return List.of();
    }
    return roles.stream().map(String::valueOf).toList();
  }
}
