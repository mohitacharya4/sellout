package dev.sellout.platform.security;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Bridges {@code sellout.oidc.*} to Spring Security's resource-server properties at the lowest
 * precedence, so a service only configures {@code SELLOUT_OIDC_ISSUER} and {@code
 * SELLOUT_OIDC_AUDIENCE}. Placeholders resolve lazily against the final environment.
 */
public final class SelloutSecurityEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered {

  static final String SOURCE_NAME = "sellout-security-bridge";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    environment
        .getPropertySources()
        .addLast(
            new MapPropertySource(
                SOURCE_NAME,
                Map.of(
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                    "${sellout.oidc.issuer}",
                    "spring.security.oauth2.resourceserver.jwt.audiences",
                    "${sellout.oidc.audience}")));
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
