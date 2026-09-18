package dev.sellout.inventory.application;

import dev.sellout.inventory.domain.Event;
import dev.sellout.inventory.domain.EventId;
import java.util.Optional;

/** Outbound port. Adapters implement it; the application layer never sees JPA. */
public interface EventRepository {
  Optional<Event> findById(EventId id);
}
