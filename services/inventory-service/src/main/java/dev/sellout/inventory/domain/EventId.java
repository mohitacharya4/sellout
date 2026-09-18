package dev.sellout.inventory.domain;

import java.util.Objects;
import java.util.UUID;

public record EventId(UUID value) {

  public EventId {
    Objects.requireNonNull(value, "value");
  }

  public static EventId of(String value) {
    return new EventId(UUID.fromString(value));
  }
}
