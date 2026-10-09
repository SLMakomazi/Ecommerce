package com.example.ecommerce.order.service;

import com.example.ecommerce.order.model.*;
import com.example.ecommerce.order.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.*;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CheckoutWorker {
  private static final Logger log = LoggerFactory.getLogger(CheckoutWorker.class);
  private final OrderRepository orders;
  private final OutboxRepository outbox;
  private final RestClient catalog, payment;
  private final MeterRegistry metrics;
  private final JsonMapper json = JsonMapper.builder().build();

  public CheckoutWorker(
      OrderRepository orders,
      OutboxRepository outbox,
      RestClient.Builder client,
      MeterRegistry metrics,
      @Value("${app.catalog-url}") String catalogUrl,
      @Value("${app.payment-url}") String paymentUrl,
      @Value("${app.internal-key}") String key) {
    this.orders = orders;
    this.outbox = outbox;
    this.metrics = metrics;
    catalog = client.clone().baseUrl(catalogUrl).defaultHeader("X-Service-Key", key).build();
    payment = client.clone().baseUrl(paymentUrl).defaultHeader("X-Service-Key", key).build();
  }

  @Transactional
  public void tick() {
    var work = orders.nextWork();
    if (work.isEmpty()) return;
    var o = work.getFirst();
    try {
      switch (o.getStatus()) {
        case "RECEIVED" -> {
          try {
            String snapshot =
                catalog
                    .post()
                    .uri("/internal/reservations/{id}", o.getId())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(o.getItems())
                    .retrieve()
                    .body(String.class);
            var node = json.readTree(snapshot);
            if (!node.path("state").asString().equals("HELD"))
              throw new IllegalStateException("Reservation not held");
            o.setSnapshot(snapshot);
            o.setTotal(new BigDecimal(node.path("total").asString()));
            o.setStatus("RESERVED");
          } catch (HttpClientErrorException.Conflict e) {
            o.setStatus("REJECTED");
            o.setFailureReason("Catalog rejected checkout (stock or order limit)");
            event(o);
          }
        }
        case "RESERVED" -> {
          String result =
              payment
                  .post()
                  .uri("/internal/payments")
                  .body(
                      Map.of(
                          "orderId",
                          o.getId(),
                          "customerId",
                          o.getCustomerId(),
                          "amount",
                          o.getTotal().toPlainString(),
                          "currency",
                          o.getCurrency(),
                          "token",
                          o.getPaymentToken()))
                  .retrieve()
                  .body(String.class);
          String state = json.readTree(result).path("status").asString();
          if (state.equals("SUCCEEDED")) o.setStatus("PAID");
          else if (state.equals("DECLINED")) {
            o.setStatus("DECLINED");
            o.setFailureReason("Payment declined");
          } else throw new IllegalStateException("Unresolved payment state");
        }
        case "PAID" -> {
          catalog
              .post()
              .uri("/internal/reservations/{id}/commit", o.getId())
              .retrieve()
              .toBodilessEntity();
          o.setStatus("CONFIRMED");
          event(o);
        }
        case "DECLINED" -> {
          catalog
              .post()
              .uri("/internal/reservations/{id}/release", o.getId())
              .retrieve()
              .toBodilessEntity();
          o.setStatus("REJECTED");
          event(o);
        }
        case "CANCEL_PENDING" -> {
          catalog
              .post()
              .uri("/internal/reservations/{id}/release", o.getId())
              .retrieve()
              .toBodilessEntity();
          o.setStatus("CANCELLED");
          event(o);
        }
        case "REFUND_PENDING" -> {
          payment
              .post()
              .uri("/internal/payments/{id}/refund", o.getId())
              .retrieve()
              .toBodilessEntity();
          o.setStatus("REFUNDED");
          event(o);
        }
        default -> throw new IllegalStateException("Invalid worker state");
      }
      o.setAttempts(0);
      o.setNextAttempt(Instant.now());
      log.info("order_transition order_id={} state={}", o.getId(), o.getStatus());
    } catch (RuntimeException e) {
      // Keep the persisted saga step. Retrying the same order identity is safe even
      // when a downstream service committed but its HTTP response was lost.
      o.setAttempts(Math.min(o.getAttempts() + 1, 1000));
      o.setNextAttempt(Instant.now().plusSeconds(Math.min(30, 2L * o.getAttempts())));
      metrics.counter("checkout_retries_total").increment();
      log.warn(
          "checkout_retry order_id={} state={} exception={}",
          o.getId(),
          o.getStatus(),
          e.getClass().getSimpleName());
    }
  }

  private void event(PurchaseOrder o) {
    var e = new OutboxEvent();
    e.setId(UUID.randomUUID());
    e.setCreatedAt(Instant.now());
    e.setPayload(
        json.writeValueAsString(
            Map.of(
                "eventId",
                e.getId(),
                "type",
                "order." + o.getStatus().toLowerCase(Locale.ROOT),
                "orderId",
                o.getId(),
                "customerId",
                o.getCustomerId(),
                "status",
                o.getStatus(),
                "amount",
                o.getTotal() == null ? "0.00" : o.getTotal().toPlainString(),
                "currency",
                o.getCurrency())));
    outbox.save(e);
    metrics.counter("orders_terminal_total", "status", o.getStatus()).increment();
  }
}
