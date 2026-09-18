package dev.sellout.platform.health;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Container health check for distroless images (no shell, no curl). Run as {@code java -cp
 * "/app/libs/*" dev.sellout.platform.health.HealthCheck}; exits 0 when {@code /healthz} is 200.
 */
public final class HealthCheck {

  private static final Duration TIMEOUT = Duration.ofSeconds(3);

  private HealthCheck() {}

  public static void main(String[] args) {
    String port = System.getenv().getOrDefault("SERVER_PORT", "8080");
    URI uri = URI.create("http://127.0.0.1:" + port + "/healthz");
    System.exit(isHealthy(uri, TIMEOUT) ? 0 : 1);
  }

  static boolean isHealthy(URI uri, Duration timeout) {
    try (HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build()) {
      HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
      HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
      return response.statusCode() == 200;
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
