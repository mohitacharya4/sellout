package dev.sellout.inventory.adapters.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataEventRepository extends JpaRepository<EventJpaEntity, UUID> {}
