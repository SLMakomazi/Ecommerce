package com.example.ecommerce.order.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.example.ecommerce.order.model.PurchaseOrder;
import com.example.ecommerce.order.repository.OrderRepository;
import com.example.ecommerce.order.repository.OutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CheckoutWorkerTest {
  private final OrderRepository orders = mock(OrderRepository.class);
  private final OutboxRepository outbox = mock(OutboxRepository.class);
  private final RestClient.Builder client = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
  private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
  private final CheckoutWorker worker =
      new CheckoutWorker(
          orders, outbox, client, metrics, "http://catalog", "http://payment", "test-service-key");

  private PurchaseOrder order(String state) {
    var order = new PurchaseOrder();
    order.setId(UUID.randomUUID());
    order.setCustomerId(UUID.randomUUID());
    order.setStatus(state);
    order.setTotal(new BigDecimal("10.50"));
    order.setCurrency("ZAR");
    order.setPaymentToken("sandbox-success");
    order.setItems("{\"items\":[]}");
    when(orders.nextWork()).thenAnswer(invocation -> List.of(order));
    return order;
  }

  @Test
  void unavailablePaymentRetainsReservationAndRetriesSameIdentity() {
    var order = order("RESERVED");
    server
        .expect(requestTo("http://payment/internal/payments"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Service-Key", "test-service-key"))
        .andExpect(jsonPath("$.orderId").value(order.getId().toString()))
        .andRespond(withServerError());
    worker.tick();
    assertEquals("RESERVED", order.getStatus());
    assertEquals(1, order.getAttempts());
    verifyNoInteractions(outbox);
    server.verify();
    server.reset();
    server
        .expect(requestTo("http://payment/internal/payments"))
        .andExpect(jsonPath("$.orderId").value(order.getId().toString()))
        .andRespond(withSuccess("{\"status\":\"SUCCEEDED\"}", MediaType.APPLICATION_JSON));
    worker.tick();
    assertEquals("PAID", order.getStatus());
    assertEquals(0, order.getAttempts());
    verifyNoInteractions(outbox);
    server.verify();
  }

  @Test
  void paymentDeclineReleasesStockBeforeTerminalEvent() {
    var order = order("DECLINED");
    server
        .expect(requestTo("http://catalog/internal/reservations/" + order.getId() + "/release"))
        .andRespond(withServerError());
    worker.tick();
    assertEquals("DECLINED", order.getStatus());
    verifyNoInteractions(outbox);
    server.verify();
    server.reset();
    server
        .expect(requestTo("http://catalog/internal/reservations/" + order.getId() + "/release"))
        .andRespond(withSuccess());
    worker.tick();
    assertEquals("REJECTED", order.getStatus());
    verify(outbox, times(1)).save(any());
    server.verify();
  }

  @Test
  void unknownPaymentResponseNeverConfirmsOrReleasesStock() {
    var order = order("RESERVED");
    server
        .expect(requestTo("http://payment/internal/payments"))
        .andRespond(withSuccess("{\"status\":\"PENDING\"}", MediaType.APPLICATION_JSON));
    worker.tick();
    assertEquals("RESERVED", order.getStatus());
    verifyNoInteractions(outbox);
    assertEquals(1, order.getAttempts());
    server.verify();
  }

  @Test
  void cancellationWaitsForInventoryReleaseBeforePublishing() {
    var order = order("CANCEL_PENDING");
    server
        .expect(requestTo("http://catalog/internal/reservations/" + order.getId() + "/release"))
        .andRespond(withServerError());
    worker.tick();
    assertEquals("CANCEL_PENDING", order.getStatus());
    verifyNoInteractions(outbox);
    server.verify();
    server.reset();
    server
        .expect(requestTo("http://catalog/internal/reservations/" + order.getId() + "/release"))
        .andRespond(withSuccess());
    worker.tick();
    assertEquals("CANCELLED", order.getStatus());
    verify(outbox).save(any());
    server.verify();
  }
}
