package com.example.ecommerce.order.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
  @Id private UUID id;

  @Column(nullable = false, columnDefinition = "text")
  private String payload;

  @Column(nullable = false)
  private boolean published;

  @Column(nullable = false)
  private Instant createdAt;

  public OutboxEvent() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID value) {
    this.id = value;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String value) {
    this.payload = value;
  }

  public boolean getPublished() {
    return published;
  }

  public void setPublished(boolean value) {
    this.published = value;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant value) {
    this.createdAt = value;
  }
}
