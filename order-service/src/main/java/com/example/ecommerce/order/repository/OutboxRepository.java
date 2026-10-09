package com.example.ecommerce.order.repository;

import com.example.ecommerce.order.model.OutboxEvent;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
  @Query(
      value =
          "SELECT * FROM outbox_events WHERE published=false ORDER BY created_at LIMIT 1 FOR UPDATE"
              + " SKIP LOCKED",
      nativeQuery = true)
  List<OutboxEvent> nextWork();
}
