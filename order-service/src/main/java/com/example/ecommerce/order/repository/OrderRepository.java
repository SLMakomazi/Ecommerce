package com.example.ecommerce.order.repository;

import com.example.ecommerce.order.model.PurchaseOrder;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface OrderRepository extends JpaRepository<PurchaseOrder, UUID> {
  Optional<PurchaseOrder> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

  List<PurchaseOrder> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable page);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from PurchaseOrder o where o.id=:id")
  Optional<PurchaseOrder> lockById(UUID id);

  @Query(
      value =
          "SELECT * FROM purchase_orders WHERE status IN"
              + " ('RECEIVED','RESERVED','PAID','DECLINED','REFUND_PENDING','CANCEL_PENDING') AND"
              + " next_attempt<=now() ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<PurchaseOrder> nextWork();
}
