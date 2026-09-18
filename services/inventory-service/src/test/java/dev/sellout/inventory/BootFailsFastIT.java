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
            "management.tracing.enabled", "false",
            "sellout.oidc.audience", "sellout-api"));

    // spring.datasource.url must be passed as a command-line arg, not a default property:
    // application.yaml's own non-blank default for this key takes precedence over
    // SpringApplication.setDefaultProperties(), which is the lowest-priority source. Without
    // this, boot would fail on the datasource connecting to the wrong port rather than on the
    // missing issuer this test is asserting on. The username and password no longer have
    // application.yaml defaults (SELLOUT_DB_USER/SELLOUT_DB_PASSWORD are required), but are
    // passed the same way for consistency. server.port=0 is likewise passed as a command-line
    // arg, not a default property, because sellout-defaults.yaml's server.port: 8080 outranks
    // SpringApplication.setDefaultProperties().
    assertThatThrownBy(
            () ->
                app.run(
                        "--server.port=0",
                        "--sellout.oidc.issuer=",
                        "--spring.datasource.url=" + SelloutContainers.POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + SelloutContainers.POSTGRES.getUsername(),
                        "--spring.datasource.password=" + SelloutContainers.POSTGRES.getPassword())
                    .close())
        .hasStackTraceContaining("sellout.oidc.issuer");
  }
}
