package com.example.ecommerce.order.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "carts")
public class Cart {
  @Id private UUID id;

  @Column(nullable = false, columnDefinition = "text")
  private String items;

  public Cart() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID value) {
    this.id = value;
  }

  public String getItems() {
    return items;
  }

  public void setItems(String value) {
    this.items = value;
  }
}
