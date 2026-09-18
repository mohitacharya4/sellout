package dev.sellout.platform.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HealthCheckTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(2);
  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void isHealthyWhenHealthzReturns200() throws IOException {
    URI uri = startServerReturning(200);

    assertThat(HealthCheck.isHealthy(uri, TIMEOUT)).isTrue();
  }

  @Test
  void isNotHealthyWhenHealthzReturns503() throws IOException {
    URI uri = startServerReturning(503);

    assertThat(HealthCheck.isHealthy(uri, TIMEOUT)).isFalse();
  }

  @Test
  void isNotHealthyWhenNothingListens() {
    URI uri = URI.create("http://127.0.0.1:1/healthz");

    assertThat(HealthCheck.isHealthy(uri, TIMEOUT)).isFalse();
  }

  @SuppressWarnings("AddressSelection") // literal loopback IP, not a hostname to resolve
  private URI startServerReturning(int status) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/healthz",
        exchange -> {
          exchange.sendResponseHeaders(status, -1);
          exchange.close();
        });
    server.start();
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/healthz");
  }
}
