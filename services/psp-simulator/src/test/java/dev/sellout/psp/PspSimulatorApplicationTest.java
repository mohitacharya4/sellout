package dev.sellout.psp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "management.tracing.enabled=false",
      "sellout.oidc.issuer=http://localhost:8180/realms/sellout"
    })
class PspSimulatorApplicationTest {

  @Autowired private Environment environment;

  @Test
  void bootsAndAnswersHealthz() {
    int status =
        RestClient.create()
            .get()
            .uri(
                "http://localhost:"
                    + environment.getRequiredProperty("local.server.port")
                    + "/healthz")
            .exchange((request, response) -> response.getStatusCode().value());

    assertThat(status).isEqualTo(200);
  }
}
