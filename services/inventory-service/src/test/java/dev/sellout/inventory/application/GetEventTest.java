package dev.sellout.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sellout.inventory.domain.Event;
import dev.sellout.inventory.domain.EventId;
import dev.sellout.inventory.domain.EventNotFoundException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetEventTest {

  private static final EventId ID = EventId.of("11111111-1111-1111-1111-111111111111");
  private static final Event EVENT =
      new Event(
          ID,
          "Winter Gala",
          Instant.parse("2026-12-24T19:00:00Z"),
          Instant.parse("2026-10-01T10:00:00Z"),
          Instant.parse("2026-10-01T12:00:00Z"));

  private final InMemoryEventRepository repository = new InMemoryEventRepository();
  private final GetEvent getEvent = new GetEvent(repository);

  @Test
  void returnsTheEventWhenItExists() {
    repository.save(EVENT);

    assertThat(getEvent.handle(ID)).isEqualTo(EVENT);
  }

  @Test
  void throwsWhenTheEventDoesNotExist() {
    assertThatThrownBy(() -> getEvent.handle(ID))
        .isInstanceOf(EventNotFoundException.class)
        .hasMessageContaining(ID.value().toString());
  }

  /** In-memory port: use cases are tested without Spring or a database. */
  static final class InMemoryEventRepository implements EventRepository {
    private final Map<EventId, Event> events = new HashMap<>();

    void save(Event event) {
      events.put(event.id(), event);
    }

    @Override
    public Optional<Event> findById(EventId id) {
      return Optional.ofNullable(events.get(id));
    }
  }
}
