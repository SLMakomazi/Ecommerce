package com.example.ecommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public record CartRequest(@NotEmpty @Size(max = 50) List<@Valid Item> items) {
  public record Item(@NotNull UUID productId, @Min(1) @Max(100) int quantity) {}
}
