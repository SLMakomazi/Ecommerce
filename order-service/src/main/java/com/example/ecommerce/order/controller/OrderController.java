package com.example.ecommerce.order.controller;

import com.example.ecommerce.order.dto.*;
import com.example.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class OrderController {
  private final OrderService service;

  public OrderController(OrderService service) {
    this.service = service;
  }

  private UUID customer(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }

  @GetMapping("/api/cart")
  public CartRequest cart(@AuthenticationPrincipal Jwt jwt) {
    return service.cart(customer(jwt));
  }

  @PutMapping("/api/cart")
  public CartRequest cart(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CartRequest r) {
    return service.saveCart(customer(jwt), r);
  }

  @PostMapping("/api/orders")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public OrderResponse checkout(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody CheckoutRequest r) {
    return service.checkout(customer(jwt), key, r);
  }

  @GetMapping("/api/orders")
  public List<OrderResponse> list(
      @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page) {
    return service.list(customer(jwt), page);
  }

  @GetMapping("/api/orders/{id}")
  public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return service.get(customer(jwt), id);
  }

  @PostMapping("/api/orders/{id}/cancel")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return service.cancel(customer(jwt), id);
  }

  @PostMapping("/api/orders/{id}/refund")
  @PreAuthorize("hasRole('admin')")
  public OrderResponse refund(@PathVariable UUID id) {
    return service.refund(id);
  }
}
