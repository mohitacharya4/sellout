package dev.sellout.inventory.adapters.out.persistence;

import dev.sellout.inventory.domain.Event;
import dev.sellout.inventory.domain.EventId;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds the demo event for compose, kind, and integration tests. Never active in production. */
@Component
@Profile("demo")
class DemoDataSeeder implements ApplicationRunner {

  static final EventId DEMO_EVENT_ID = EventId.of("11111111-1111-1111-1111-111111111111");

  private final SpringDataEventRepository repository;

  DemoDataSeeder(SpringDataEventRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (repository.existsById(DEMO_EVENT_ID.value())) {
      return;
    }
    repository.save(
        EventJpaEntity.fromDomain(
            new Event(
                DEMO_EVENT_ID,
                "Winter Gala",
                Instant.parse("2026-12-24T19:00:00Z"),
                Instant.parse("2026-10-01T10:00:00Z"),
                Instant.parse("2026-10-01T12:00:00Z"))));
  }
}
