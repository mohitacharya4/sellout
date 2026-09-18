package dev.sellout.inventory.application;

import dev.sellout.inventory.domain.Event;
import dev.sellout.inventory.domain.EventId;
import dev.sellout.inventory.domain.EventNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inbound port + use case. Authorization lives here, not in the controller (Constitution §4.2). */
@Service
@Transactional(readOnly = true)
public class GetEvent {

  private final EventRepository events;

  public GetEvent(EventRepository events) {
    this.events = events;
  }

  @PreAuthorize("hasAnyRole('CUSTOMER', 'ORGANIZER', 'ADMIN')")
  public Event handle(EventId id) {
    return events.findById(id).orElseThrow(() -> new EventNotFoundException(id));
  }
}
