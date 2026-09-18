package dev.sellout.inventory.adapters.in.web;

import dev.sellout.inventory.application.GetEvent;
import dev.sellout.inventory.domain.Event;
import dev.sellout.inventory.domain.EventId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
class EventController {

  private final GetEvent getEvent;

  EventController(GetEvent getEvent) {
    this.getEvent = getEvent;
  }

  @GetMapping("/{id}")
  EventResponse get(@PathVariable UUID id) {
    return EventResponse.from(getEvent.handle(new EventId(id)));
  }

  record EventResponse(
      UUID id, String name, Instant startsAt, Instant saleOpensAt, Instant saleClosesAt) {
    static EventResponse from(Event event) {
      return new EventResponse(
          event.id().value(),
          event.name(),
          event.startsAt(),
          event.saleOpensAt(),
          event.saleClosesAt());
    }
  }
}
