package dev.sellout.inventory.domain;

import java.time.Instant;
import java.util.Objects;

/** An event whose seats go on sale in a window that closes no later than the event starts. */
public record Event(
    EventId id, String name, Instant startsAt, Instant saleOpensAt, Instant saleClosesAt) {

  public Event {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(startsAt, "startsAt");
    Objects.requireNonNull(saleOpensAt, "saleOpensAt");
    Objects.requireNonNull(saleClosesAt, "saleClosesAt");
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (!saleClosesAt.isAfter(saleOpensAt)) {
      throw new IllegalArgumentException("saleClosesAt must be after saleOpensAt");
    }
    if (saleClosesAt.isAfter(startsAt)) {
      throw new IllegalArgumentException("saleClosesAt must not be after startsAt");
    }
  }
}
