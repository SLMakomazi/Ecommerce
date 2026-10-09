package com.example.ecommerce.catalog.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {
  @Id private UUID id;

  @Column(nullable = false)
  private String state;

  @Column(nullable = false)
  private String requestHash;

  @Column(nullable = false, columnDefinition = "text")
  private String snapshot;

  @Column(nullable = false)
  private Instant createdAt;

  public Reservation() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID value) {
    this.id = value;
  }

  public String getState() {
    return state;
  }

  public void setState(String value) {
    this.state = value;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public void setRequestHash(String value) {
    this.requestHash = value;
  }

  public String getSnapshot() {
    return snapshot;
  }

  public void setSnapshot(String value) {
    this.snapshot = value;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant value) {
    this.createdAt = value;
  }
}
