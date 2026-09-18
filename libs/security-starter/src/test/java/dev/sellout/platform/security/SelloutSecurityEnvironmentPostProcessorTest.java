package dev.sellout.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class SelloutSecurityEnvironmentPostProcessorTest {

  @Test
  void bridgesSelloutOidcPropertiesToSpringSecurity() {
    MockEnvironment env =
        new MockEnvironment()
            .withProperty("sellout.oidc.issuer", "http://localhost:8180/realms/sellout")
            .withProperty("sellout.oidc.audience", "sellout-api");

    new SelloutSecurityEnvironmentPostProcessor()
        .postProcessEnvironment(env, new SpringApplication());

    assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
        .isEqualTo("http://localhost:8180/realms/sellout");
    assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.audiences"))
        .isEqualTo("sellout-api");
  }

  @Test
  void explicitSpringSecurityPropertiesStillWin() {
    MockEnvironment env =
        new MockEnvironment()
            .withProperty("sellout.oidc.issuer", "http://localhost:8180/realms/sellout")
            .withProperty(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                "http://keycloak:8080/realms/sellout/protocol/openid-connect/certs");

    new SelloutSecurityEnvironmentPostProcessor()
        .postProcessEnvironment(env, new SpringApplication());

    assertThat(env.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri"))
        .isEqualTo("http://keycloak:8080/realms/sellout/protocol/openid-connect/certs");
  }
}
