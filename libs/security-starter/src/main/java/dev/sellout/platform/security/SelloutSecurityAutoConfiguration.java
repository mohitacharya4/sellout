package dev.sellout.platform.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Zero-trust baseline (Constitution §4): every service validates the bearer JWT itself. Only the
 * probes and actuator are public. Ownership rules live in the application layer via method
 * security, never here.
 */
@AutoConfiguration
@EnableMethodSecurity
@EnableConfigurationProperties(SelloutOidcProperties.class)
public class SelloutSecurityAutoConfiguration {

  @Bean
  public JwtAuthenticationConverter selloutJwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new KeycloakGrantedAuthoritiesConverter());
    return converter;
  }

  @Bean
  public SecurityFilterChain selloutSecurityFilterChain(
      HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/healthz", "/readyz", "/actuator/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .build();
  }
}
