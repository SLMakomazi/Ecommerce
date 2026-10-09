package com.example.ecommerce.order.service;

import com.example.ecommerce.order.dto.*;
import com.example.ecommerce.order.exception.ApiException;
import com.example.ecommerce.order.mapper.OrderMapper;
import com.example.ecommerce.order.model.*;
import com.example.ecommerce.order.repository.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OrderService {
  private final CartRepository carts;
  private final OrderRepository orders;
  private final EntityManager em;
  private final JsonMapper json = JsonMapper.builder().build();

  public OrderService(CartRepository carts, OrderRepository orders, EntityManager em) {
    this.carts = carts;
    this.orders = orders;
    this.em = em;
  }

  @Transactional
  public CartRequest saveCart(UUID customer, CartRequest r) {
    lockCustomer(customer);
    var counts = new TreeMap<UUID, Integer>();
    for (var i : r.items()) counts.merge(i.productId(), i.quantity(), Integer::sum);
    if (counts.values().stream().anyMatch(q -> q > 100))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Maximum quantity exceeded");
    var c = carts.findById(customer).orElseGet(Cart::new);
    c.setId(customer);
    c.setItems(
        json.writeValueAsString(
            new CartRequest(
                counts.entrySet().stream()
                    .map(e -> new CartRequest.Item(e.getKey(), e.getValue()))
                    .toList())));
    carts.save(c);
    return json.readValue(c.getItems(), CartRequest.class);
  }

  @Transactional(readOnly = true)
  public CartRequest cart(UUID customer) {
    return carts
        .findById(customer)
        .map(c -> json.readValue(c.getItems(), CartRequest.class))
        .orElse(new CartRequest(List.of()));
  }

  @Transactional
  public OrderResponse checkout(UUID customer, String key, CheckoutRequest request) {
    if (key == null || !key.matches("[A-Za-z0-9_-]{8,100}"))
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "An 8-100 character Idempotency-Key is required");
    lockCustomer(customer);
    String hash = json.writeValueAsString(request);
    var prior = orders.findByCustomerIdAndIdempotencyKey(customer, key);
    if (prior.isPresent()) {
      if (!prior.get().getRequestHash().equals(hash))
        throw new ApiException(
            HttpStatus.CONFLICT, "Idempotency key reused with different checkout details");
      return OrderMapper.response(prior.get());
    }
    var cart =
        carts
            .findById(customer)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Cart is empty"));
    if (json.readValue(cart.getItems(), CartRequest.class).items().isEmpty())
      throw new ApiException(HttpStatus.BAD_REQUEST, "Cart is empty");
    var o = new PurchaseOrder();
    o.setId(UUID.randomUUID());
    o.setCustomerId(customer);
    o.setIdempotencyKey(key);
    o.setRequestHash(hash);
    o.setItems(cart.getItems());
    o.setAddress(request.address());
    o.setPaymentToken(request.paymentToken());
    o.setStatus("RECEIVED");
    o.setCurrency("ZAR");
    o.setCreatedAt(Instant.now());
    o.setNextAttempt(Instant.now());
    orders.save(o);
    carts.delete(cart);
    return OrderMapper.response(o);
  }

  @Transactional(readOnly = true)
  public OrderResponse get(UUID customer, UUID id) {
    return OrderMapper.response(owned(customer, id));
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> list(UUID customer, int page) {
    return orders
        .findByCustomerIdOrderByCreatedAtDesc(customer, PageRequest.of(Math.max(0, page), 20))
        .stream()
        .map(OrderMapper::response)
        .toList();
  }

  @Transactional
  public OrderResponse cancel(UUID customer, UUID id) {
    var o =
        orders
            .lockById(id)
            .filter(x -> x.getCustomerId().equals(customer))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
    if (!o.getStatus().equals("RECEIVED"))
      throw new ApiException(HttpStatus.CONFLICT, "Only an unprocessed order can be cancelled");
    o.setStatus("CANCEL_PENDING");
    o.setNextAttempt(Instant.now());
    return OrderMapper.response(o);
  }

  @Transactional
  public OrderResponse refund(UUID id) {
    var o =
        orders
            .lockById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
    if (o.getStatus().equals("REFUNDED") || o.getStatus().equals("REFUND_PENDING"))
      return OrderMapper.response(o);
    if (!o.getStatus().equals("CONFIRMED"))
      throw new ApiException(HttpStatus.CONFLICT, "Only a confirmed order can be refunded");
    o.setStatus("REFUND_PENDING");
    o.setNextAttempt(Instant.now());
    return OrderMapper.response(o);
  }

  private void lockCustomer(UUID customer) {
    em.createNativeQuery("select pg_advisory_xact_lock(:key)")
        .setParameter("key", customer.getMostSignificantBits() ^ customer.getLeastSignificantBits())
        .getSingleResult();
  }

  private PurchaseOrder owned(UUID customer, UUID id) {
    return orders
        .findById(id)
        .filter(o -> o.getCustomerId().equals(customer))
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
  }
}
