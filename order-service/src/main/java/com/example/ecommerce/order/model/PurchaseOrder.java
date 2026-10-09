package com.example.ecommerce.order.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
  @Id private UUID id;

  @Column(nullable = false)
  private UUID customerId;

  @Column(nullable = false)
  private String idempotencyKey;

  @Column(nullable = false, columnDefinition = "text")
  private String requestHash;

  @Column(nullable = false, columnDefinition = "text")
  private String items;

  @Column(columnDefinition = "text")
  private String snapshot;

  @Column(nullable = false, length = 1000)
  private String address;

  @Column(nullable = false)
  private String paymentToken;

  @Column(nullable = false)
  private String status;

  @Column(precision = 19, scale = 2)
  private BigDecimal total;

  @Column(nullable = false)
  private String currency;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant nextAttempt;

  @Column(nullable = false)
  private int attempts;

  private String failureReason;

  public PurchaseOrder() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID value) {
    this.id = value;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public void setCustomerId(UUID value) {
    this.customerId = value;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String value) {
    this.idempotencyKey = value;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public void setRequestHash(String value) {
    this.requestHash = value;
  }

  public String getItems() {
    return items;
  }

  public void setItems(String value) {
    this.items = value;
  }

  public String getSnapshot() {
    return snapshot;
  }

  public void setSnapshot(String value) {
    this.snapshot = value;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String value) {
    this.address = value;
  }

  public String getPaymentToken() {
    return paymentToken;
  }

  public void setPaymentToken(String value) {
    this.paymentToken = value;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    this.status = value;
  }

  public BigDecimal getTotal() {
    return total;
  }

  public void setTotal(BigDecimal value) {
    this.total = value;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String value) {
    this.currency = value;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant value) {
    this.createdAt = value;
  }

  public Instant getNextAttempt() {
    return nextAttempt;
  }

  public void setNextAttempt(Instant value) {
    this.nextAttempt = value;
  }

  public int getAttempts() {
    return attempts;
  }

  public void setAttempts(int value) {
    this.attempts = value;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String value) {
    this.failureReason = value;
  }
}
