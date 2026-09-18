package dev.sellout.testing.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A {@link Clock} that tests move by hand. Never use {@code Thread.sleep} to wait for time. */
public final class MutableClock extends Clock {

  private final ZoneId zone;
  private volatile Instant instant;

  private MutableClock(Instant instant, ZoneId zone) {
    this.instant = instant;
    this.zone = zone;
  }

  public static MutableClock at(Instant instant) {
    return new MutableClock(instant, ZoneOffset.UTC);
  }

  public void advance(Duration duration) {
    instant = instant.plus(duration);
  }

  public void set(Instant newInstant) {
    instant = newInstant;
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  /**
   * Returns an independent snapshot clock in {@code newZone}: advancing or setting the returned
   * clock does not affect this one, or vice versa.
   */
  @Override
  public Clock withZone(ZoneId newZone) {
    return new MutableClock(instant, newZone);
  }

  @Override
  public Instant instant() {
    return instant;
  }
}
