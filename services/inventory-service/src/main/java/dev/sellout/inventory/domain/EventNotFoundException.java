package dev.sellout.inventory.domain;

public class EventNotFoundException extends RuntimeException {

  private final EventId eventId;

  public EventNotFoundException(EventId eventId) {
    super("Event not found: " + eventId.value());
    this.eventId = eventId;
  }

  public EventId eventId() {
    return eventId;
  }
}
