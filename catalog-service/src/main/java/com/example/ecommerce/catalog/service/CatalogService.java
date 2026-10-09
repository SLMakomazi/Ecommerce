package com.example.ecommerce.catalog.service;

import com.example.ecommerce.catalog.dto.*;
import com.example.ecommerce.catalog.exception.ApiException;
import com.example.ecommerce.catalog.mapper.ProductMapper;
import com.example.ecommerce.catalog.model.*;
import com.example.ecommerce.catalog.repository.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CatalogService {
  private final ProductRepository products;
  private final ReservationRepository reservations;
  private final EntityManager em;
  private final JsonMapper json = JsonMapper.builder().build();

  public CatalogService(
      ProductRepository products, ReservationRepository reservations, EntityManager em) {
    this.products = products;
    this.reservations = reservations;
    this.em = em;
  }

  @Transactional(readOnly = true)
  public List<ProductResponse> list(int page, int size) {
    return products
        .findAll(
            PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)), Sort.by("name")))
        .map(ProductMapper::response)
        .getContent();
  }

  @Transactional(readOnly = true)
  public ProductResponse get(UUID id) {
    return ProductMapper.response(
        products
            .findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found")));
  }

  @Transactional
  public ProductResponse create(ProductRequest r) {
    var p = new Product();
    p.setId(UUID.randomUUID());
    p.setSku(r.sku());
    p.setName(r.name());
    p.setDescription(r.description());
    p.setPrice(r.price());
    p.setCurrency(r.currency());
    p.setAvailableStock(r.stock());
    return ProductMapper.response(products.save(p));
  }

  @Transactional
  public ProductResponse update(UUID id, ProductUpdateRequest request) {
    var product =
        products
            .lockById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
    product.setName(request.name());
    product.setDescription(request.description());
    product.setPrice(request.price());
    return ProductMapper.response(product);
  }

  @Transactional
  public ProductResponse restock(UUID id, int quantity) {
    if (quantity < 1 || quantity > 100000)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid restock quantity");
    var p =
        products
            .lockById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
    p.setAvailableStock(Math.addExact(p.getAvailableStock(), quantity));
    return ProductMapper.response(p);
  }

  private void lock(UUID id) {
    em.createNativeQuery("select pg_advisory_xact_lock(:key)")
        .setParameter("key", id.getMostSignificantBits() ^ id.getLeastSignificantBits())
        .getSingleResult();
  }

  @Transactional
  public ReservationResponse reserve(UUID orderId, ReservationRequest request) {
    lock(orderId);
    // Canonical sorted items both avoid product-lock deadlocks and detect changed retries.
    var counts = new TreeMap<UUID, Integer>();
    for (var i : request.items()) counts.merge(i.productId(), i.quantity(), Integer::sum);
    if (counts.values().stream().anyMatch(q -> q > 100))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Maximum quantity exceeded");
    String canonical = json.writeValueAsString(counts);
    var existing = reservations.findById(orderId);
    if (existing.isPresent()) {
      if (!existing.get().getRequestHash().equals(canonical))
        throw new ApiException(HttpStatus.CONFLICT, "Reservation key reused with different items");
      return snapshot(existing.get());
    }
    var lines = new ArrayList<ReservationResponse.Line>();
    BigDecimal total = BigDecimal.ZERO;
    for (var entry : counts.entrySet()) {
      var p =
          products
              .lockById(entry.getKey())
              .orElseThrow(
                  () -> new ApiException(HttpStatus.CONFLICT, "Product no longer available"));
      if (p.getAvailableStock() < entry.getValue())
        throw new ApiException(HttpStatus.CONFLICT, "Insufficient stock");
      p.setAvailableStock(p.getAvailableStock() - entry.getValue());
      var lineTotal = p.getPrice().multiply(BigDecimal.valueOf(entry.getValue()));
      total = total.add(lineTotal);
      lines.add(
          new ReservationResponse.Line(
              p.getId(), p.getSku(), p.getName(), entry.getValue(), p.getPrice(), lineTotal));
    }
    if (total.compareTo(new BigDecimal("100000000.00")) > 0) {
      throw new ApiException(HttpStatus.CONFLICT, "Checkout total exceeds sandbox payment limit");
    }
    var response = new ReservationResponse(orderId, "HELD", lines, total, "ZAR");
    var r = new Reservation();
    r.setId(orderId);
    r.setState("HELD");
    r.setRequestHash(canonical);
    r.setCreatedAt(Instant.now());
    r.setSnapshot(json.writeValueAsString(response));
    reservations.save(r);
    return response;
  }

  @Transactional
  public ReservationResponse transition(UUID id, String target) {
    lock(id);
    var r = reservations.findById(id).orElse(null);
    if (r == null && target.equals("RELEASED")) {
      // A tombstone serializes with reserve on the same advisory lock. If a
      // timed-out reservation request arrives later, it cannot recreate stock holds.
      r = new Reservation();
      r.setId(id);
      r.setState("RELEASED");
      r.setRequestHash("cancelled-before-reservation");
      r.setCreatedAt(Instant.now());
      r.setSnapshot(
          json.writeValueAsString(
              new ReservationResponse(id, "RELEASED", List.of(), BigDecimal.ZERO, "ZAR")));
      reservations.save(r);
    }
    if (r == null) throw new ApiException(HttpStatus.NOT_FOUND, "Reservation not found");
    if (r.getState().equals(target)) return snapshot(r);
    if (!r.getState().equals("HELD"))
      throw new ApiException(HttpStatus.CONFLICT, "Reservation already finalized");
    if (target.equals("RELEASED")) {
      for (var line :
          snapshot(r).lines().stream()
              .sorted(Comparator.comparing(ReservationResponse.Line::productId))
              .toList()) {
        var p = products.lockById(line.productId()).orElseThrow();
        p.setAvailableStock(p.getAvailableStock() + line.quantity());
      }
    }
    r.setState(target);
    return snapshot(r);
  }

  private ReservationResponse snapshot(Reservation r) {
    var s = json.readValue(r.getSnapshot(), ReservationResponse.class);
    return new ReservationResponse(r.getId(), r.getState(), s.lines(), s.total(), s.currency());
  }
}
