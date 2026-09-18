package dev.sellout.inventory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sellout.testing.containers.SelloutContainers;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

@Tag("integration")
class BootFailsFastIT {

  @Test
  void aMissingOidcIssuerFailsBootNamingTheProperty() {
    SelloutContainers.POSTGRES.start();
    SpringApplication app = new SpringApplication(InventoryApplication.class);
    app.setWebApplicationType(WebApplicationType.SERVLET);
    app.setDefaultProperties(
        Map.of(
            "server.port", "0",
            "management.tracing.enabled", "false",
            "sellout.oidc.audience", "sellout-api"));

    // spring.datasource.* must be passed as command-line args, not default properties:
    // application.yaml's own (non-blank) defaults for these keys take precedence over
    // SpringApplication.setDefaultProperties(), which is the lowest-priority source. Without
    // this, boot would fail on the datasource connecting to the wrong port rather than on the
    // missing issuer this test is asserting on.
    //
    // spring.security.oauth2.resourceserver.jwt.issuer-uri is also supplied directly (bypassing
    // the sellout.oidc.issuer -> issuer-uri bridge) with a placeholder value so Boot's own
    // OAuth2ResourceServerAutoConfiguration still creates a (lazy) JwtDecoder bean; otherwise the
    // security filter chain bean fails first with an unrelated "No qualifying bean of type
    // JwtDecoder" error, before SelloutOidcProperties' own validation -- the one this test
    // targets -- ever gets a chance to run. The decoder is never used to decode a token here.
    assertThatThrownBy(
            () ->
                app.run(
                        "--sellout.oidc.issuer=",
                        "--spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/placeholder-issuer",
                        "--spring.datasource.url=" + SelloutContainers.POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + SelloutContainers.POSTGRES.getUsername(),
                        "--spring.datasource.password=" + SelloutContainers.POSTGRES.getPassword())
                    .close())
        .hasStackTraceContaining("sellout.oidc.issuer");
  }
}
