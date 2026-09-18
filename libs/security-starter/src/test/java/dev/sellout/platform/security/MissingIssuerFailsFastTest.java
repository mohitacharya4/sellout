package dev.sellout.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

/**
 * A missing {@code sellout.oidc.issuer} must fail boot by naming that property, not by tripping
 * over Boot's own, unrelated {@code JwtDecoder} auto-configuration first (see {@code
 * SelloutSecurityAutoConfiguration#selloutSecurityFilterChain}).
 */
class MissingIssuerFailsFastTest {

  @Test
  void aMissingOidcIssuerFailsBootNamingTheProperty() {
    SpringApplication app = new SpringApplication(SecurityTestApplication.class);
    app.setDefaultProperties(Map.of("sellout.oidc.audience", "sellout-api"));

    // server.port=0 must be a command-line arg, not a default property: sellout-defaults.yaml's
    // server.port: 8080 outranks SpringApplication.setDefaultProperties().
    Throwable thrown =
        catchThrowable(() -> app.run("--server.port=0", "--sellout.oidc.issuer=").close());

    assertThat(thrown).isNotNull();
    String trace = stackTraceAsString(thrown);
    assertThat(trace).contains("sellout.oidc.issuer");
    assertThat(trace)
        .doesNotContain(
            "No qualifying bean of type 'org.springframework.security.oauth2.jwt.JwtDecoder'");
  }

  private static String stackTraceAsString(Throwable thrown) {
    StringWriter writer = new StringWriter();
    thrown.printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }
}
