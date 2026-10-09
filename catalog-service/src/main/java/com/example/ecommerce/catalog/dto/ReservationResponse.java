package com.example.ecommerce.catalog.dto;

import java.math.BigDecimal;
import java.util.*;

public record ReservationResponse(
    UUID orderId, String state, List<Line> lines, BigDecimal total, String currency) {
  public record Line(
      UUID productId,
      String sku,
      String name,
      int quantity,
      BigDecimal unitPrice,
      BigDecimal lineTotal) {}
}
