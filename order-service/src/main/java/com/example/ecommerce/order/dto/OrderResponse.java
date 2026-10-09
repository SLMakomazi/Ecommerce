package com.example.ecommerce.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record OrderResponse(
    UUID id,
    String status,
    BigDecimal total,
    String currency,
    JsonNode items,
    Instant createdAt,
    String failureReason) {}
