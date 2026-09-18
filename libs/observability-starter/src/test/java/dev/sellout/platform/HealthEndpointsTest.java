package dev.sellout.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.tracing.enabled=false")
class HealthEndpointsTest {

  @TestConfiguration(proxyBeanMethods = false)
  static class ToggleConfiguration {
    static final AtomicBoolean UP = new AtomicBoolean(true);

    @Bean
    HealthIndicator toggleHealthIndicator() {
      return () -> UP.get() ? Health.up().build() : Health.down().build();
    }
  }

  @Autowired private Environment environment;
  @Autowired private Clock clock;
  private final RestClient client = RestClient.create();

  @AfterEach
  void resetToggle() {
    ToggleConfiguration.UP.set(true);
  }

  @Test
  void healthzIsUpOnTheServerPort() {
    assertThat(status("/healthz")).isEqualTo(200);
  }

  @Test
  void readyzReflectsEveryHealthIndicator() {
    assertThat(status("/readyz")).isEqualTo(200);

    ToggleConfiguration.UP.set(false);

    assertThat(status("/readyz")).isEqualTo(503);
    assertThat(status("/healthz")).as("liveness ignores dependency health").isEqualTo(200);
  }

  @Test
  void prometheusEndpointExposesJvmAndHttpMetrics() {
    assertThat(status("/healthz")).isEqualTo(200);

    ResponseEntity<String> response =
        client.get().uri(url("/actuator/prometheus")).retrieve().toEntity(String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).contains("jvm_memory_used_bytes");
    assertThat(response.getBody()).contains("http_server_requests");
  }

  @Test
  void clockBeanIsUtc() {
    assertThat(clock.getZone().getId()).isEqualTo("Z");
  }

  private int status(String path) {
    return client
        .get()
        .uri(url(path))
        .exchange((request, response) -> response.getStatusCode().value());
  }

  private String url(String path) {
    return "http://localhost:" + environment.getRequiredProperty("local.server.port") + path;
  }
}
