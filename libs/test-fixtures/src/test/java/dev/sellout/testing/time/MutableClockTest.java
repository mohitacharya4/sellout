package dev.sellout.testing.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MutableClockTest {

  private static final Instant START = Instant.parse("2026-09-18T10:00:00Z");

  @Test
  void advanceMovesTheInstantForward() {
    MutableClock clock = MutableClock.at(START);

    clock.advance(Duration.ofMinutes(5));

    assertThat(clock.instant()).isEqualTo(Instant.parse("2026-09-18T10:05:00Z"));
  }

  @Test
  void setReplacesTheInstant() {
    MutableClock clock = MutableClock.at(START);

    clock.set(Instant.EPOCH);

    assertThat(clock.instant()).isEqualTo(Instant.EPOCH);
  }

  @Test
  void zoneIsUtcAndWithZoneKeepsTheInstant() {
    MutableClock clock = MutableClock.at(START);

    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    assertThat(clock.withZone(ZoneOffset.ofHours(2)).instant()).isEqualTo(START);
  }
}
