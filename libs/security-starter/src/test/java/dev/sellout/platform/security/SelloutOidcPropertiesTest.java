package dev.sellout.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class SelloutOidcPropertiesTest {

  @Test
  void bindsIssuerAndAudience() {
    SelloutOidcProperties props =
        bind(
            Map.of(
                "sellout.oidc.issuer", "http://localhost:8180/realms/sellout",
                "sellout.oidc.audience", "sellout-api"));

    assertThat(props.issuer()).isEqualTo("http://localhost:8180/realms/sellout");
    assertThat(props.audience()).isEqualTo("sellout-api");
  }

  @Test
  void failsFastNamingTheMissingProperty() {
    assertThatThrownBy(() -> bind(Map.of("sellout.oidc.audience", "sellout-api")))
        .isInstanceOf(BindException.class)
        .hasMessageContaining("sellout.oidc")
        .rootCause()
        .hasMessageContaining("issuer");
  }

  private static SelloutOidcProperties bind(Map<String, String> properties) {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    return new Binder(new MapConfigurationPropertySource(properties))
        .bind(
            "sellout.oidc",
            Bindable.of(SelloutOidcProperties.class),
            new ValidationBindHandler(validator))
        .get();
  }
}
