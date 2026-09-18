package dev.sellout.inventory.adapters.out.persistence;

import dev.sellout.inventory.domain.Event;
import dev.sellout.inventory.domain.EventId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event")
class EventJpaEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "sale_opens_at", nullable = false)
  private Instant saleOpensAt;

  @Column(name = "sale_closes_at", nullable = false)
  private Instant saleClosesAt;

  protected EventJpaEntity() {}

  static EventJpaEntity fromDomain(Event event) {
    EventJpaEntity entity = new EventJpaEntity();
    entity.id = event.id().value();
    entity.name = event.name();
    entity.startsAt = event.startsAt();
    entity.saleOpensAt = event.saleOpensAt();
    entity.saleClosesAt = event.saleClosesAt();
    return entity;
  }

  Event toDomain() {
    return new Event(new EventId(id), name, startsAt, saleOpensAt, saleClosesAt);
  }
}
