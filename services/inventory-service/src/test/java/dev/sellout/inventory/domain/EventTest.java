package dev.sellout.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class EventTest {

  private static final EventId ID = EventId.of("11111111-1111-1111-1111-111111111111");
  private static final Instant OPENS = Instant.parse("2026-10-01T10:00:00Z");
  private static final Instant CLOSES = Instant.parse("2026-10-01T12:00:00Z");
  private static final Instant STARTS = Instant.parse("2026-12-24T19:00:00Z");

  @Test
  void createsAnEventWithAValidSaleWindow() {
    Event event = new Event(ID, "Winter Gala", STARTS, OPENS, CLOSES);

    assertThat(event.id()).isEqualTo(ID);
    assertThat(event.name()).isEqualTo("Winter Gala");
  }

  @Test
  void rejectsABlankName() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Event(ID, "  ", STARTS, OPENS, CLOSES))
        .withMessageContaining("name");
  }

  @Test
  void rejectsASaleThatClosesBeforeItOpens() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Event(ID, "Winter Gala", STARTS, CLOSES, OPENS))
        .withMessageContaining("saleClosesAt");
  }

  @Test
  void rejectsASaleThatClosesAfterTheEventStarts() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Event(ID, "Winter Gala", OPENS, OPENS, STARTS))
        .withMessageContaining("startsAt");
  }

  @Test
  void eventIdParsesAndRejectsGarbage() {
    assertThat(ID.value().toString()).isEqualTo("11111111-1111-1111-1111-111111111111");
    assertThatIllegalArgumentException().isThrownBy(() -> EventId.of("not-a-uuid"));
  }
}
