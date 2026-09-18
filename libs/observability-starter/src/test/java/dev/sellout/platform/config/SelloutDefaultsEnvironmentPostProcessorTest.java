package dev.sellout.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class SelloutDefaultsEnvironmentPostProcessorTest {

  @Test
  void addsPlatformDefaultsBelowApplicationConfiguration() {
    MockEnvironment env = new MockEnvironment().withProperty("server.port", "9999");

    new SelloutDefaultsEnvironmentPostProcessor()
        .postProcessEnvironment(env, new SpringApplication());

    assertThat(env.getProperty("spring.threads.virtual.enabled")).isEqualTo("true");
    assertThat(env.getProperty("server.shutdown")).isEqualTo("graceful");
    assertThat(env.getProperty("management.endpoint.health.group.liveness.additional-path"))
        .isEqualTo("server:/healthz");
    assertThat(env.getProperty("management.endpoint.health.group.readiness.additional-path"))
        .isEqualTo("server:/readyz");
    assertThat(env.getProperty("logging.structured.format.console")).isEqualTo("ecs");
    assertThat(env.getProperty("server.port")).as("app config wins").isEqualTo("9999");
    assertThat(env.getPropertySources().get(SelloutDefaultsEnvironmentPostProcessor.SOURCE_NAME))
        .isNotNull();
  }
}
