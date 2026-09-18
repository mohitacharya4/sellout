package dev.sellout.inventory.adapters.out.persistence;

import dev.sellout.inventory.application.EventRepository;
import dev.sellout.inventory.domain.Event;
import dev.sellout.inventory.domain.EventId;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class EventPersistenceAdapter implements EventRepository {

  private final SpringDataEventRepository repository;

  EventPersistenceAdapter(SpringDataEventRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<Event> findById(EventId id) {
    return repository.findById(id.value()).map(EventJpaEntity::toDomain);
  }
}
