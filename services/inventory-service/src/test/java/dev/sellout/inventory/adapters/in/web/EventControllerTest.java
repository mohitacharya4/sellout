package dev.sellout.inventory.adapters.in.web;

import static dev.sellout.testing.security.JwtFixtures.as;
import static dev.sellout.testing.security.JwtFixtures.customer;
import static dev.sellout.testing.security.JwtFixtures.noRoles;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sellout.inventory.application.EventRepository;
import dev.sellout.inventory.application.GetEvent;
import dev.sellout.inventory.domain.Event;
import dev.sellout.inventory.domain.EventId;
import dev.sellout.platform.security.SelloutSecurityAutoConfiguration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = EventController.class,
    properties = {
      "sellout.oidc.issuer=http://localhost:8180/realms/sellout",
      "sellout.oidc.audience=sellout-api"
    })
@ImportAutoConfiguration(SelloutSecurityAutoConfiguration.class)
@Import(GetEvent.class)
class EventControllerTest {

  private static final String ID = "11111111-1111-1111-1111-111111111111";
  private static final Event EVENT =
      new Event(
          EventId.of(ID),
          "Winter Gala",
          Instant.parse("2026-12-24T19:00:00Z"),
          Instant.parse("2026-10-01T10:00:00Z"),
          Instant.parse("2026-10-01T12:00:00Z"));

  @Autowired private MockMvc mockMvc;
  @MockitoBean private EventRepository events;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void withoutATokenIs401() throws Exception {
    mockMvc.perform(get("/events/{id}", ID)).andExpect(status().isUnauthorized());
  }

  @Test
  void withATokenButNoRoleIs403() throws Exception {
    mockMvc.perform(get("/events/{id}", ID).with(as(noRoles()))).andExpect(status().isForbidden());
  }

  @Test
  void customerReadsAnEvent() throws Exception {
    when(events.findById(EventId.of(ID))).thenReturn(Optional.of(EVENT));

    mockMvc
        .perform(get("/events/{id}", ID).with(as(customer())))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        .andExpect(jsonPath("$.id").value(ID))
        .andExpect(jsonPath("$.name").value("Winter Gala"))
        .andExpect(jsonPath("$.startsAt").value("2026-12-24T19:00:00Z"));
  }

  @Test
  void unknownEventIs404ProblemDetail() throws Exception {
    when(events.findById(EventId.of(ID))).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/events/{id}", ID).with(as(customer())))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail").value("Event not found: " + ID));
  }
}
