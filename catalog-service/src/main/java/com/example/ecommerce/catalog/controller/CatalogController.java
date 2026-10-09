package com.example.ecommerce.catalog.controller;

import com.example.ecommerce.catalog.dto.*;
import com.example.ecommerce.catalog.service.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class CatalogController {
  private final CatalogService service;

  public CatalogController(CatalogService service) {
    this.service = service;
  }

  @GetMapping("/api/products")
  public List<ProductResponse> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return service.list(page, size);
  }

  @GetMapping("/api/products/{id}")
  public ProductResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/api/products")
  @ResponseStatus(HttpStatus.CREATED)
  public ProductResponse create(@Valid @RequestBody ProductRequest r) {
    return service.create(r);
  }

  @PutMapping("/api/products/{id}")
  @PreAuthorize("hasRole('admin')")
  public ProductResponse update(
      @PathVariable UUID id, @Valid @RequestBody ProductUpdateRequest request) {
    return service.update(id, request);
  }

  public record Restock(@Min(1) @Max(100000) int quantity) {}

  @PostMapping("/api/products/{id}/restock")
  @PreAuthorize("hasRole('admin')")
  public ProductResponse restock(@PathVariable UUID id, @Valid @RequestBody Restock r) {
    return service.restock(id, r.quantity());
  }

  @PostMapping("/internal/reservations/{id}")
  public ReservationResponse reserve(
      @PathVariable UUID id, @Valid @RequestBody ReservationRequest r) {
    return service.reserve(id, r);
  }

  @PostMapping("/internal/reservations/{id}/commit")
  public ReservationResponse commit(@PathVariable UUID id) {
    return service.transition(id, "COMMITTED");
  }

  @PostMapping("/internal/reservations/{id}/release")
  public ReservationResponse release(@PathVariable UUID id) {
    return service.transition(id, "RELEASED");
  }
}
