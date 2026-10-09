package com.example.ecommerce.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record ReservationRequest(@NotEmpty @Size(max = 50) List<@Valid Item> items) {
  public record Item(@NotNull UUID productId, @Min(1) @Max(100) int quantity) {}
}
