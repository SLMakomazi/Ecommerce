package com.example.ecommerce.order.mapper;

import com.example.ecommerce.order.dto.OrderResponse;
import com.example.ecommerce.order.model.PurchaseOrder;
import tools.jackson.databind.json.JsonMapper;

public final class OrderMapper {
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private OrderMapper() {}

  public static OrderResponse response(PurchaseOrder o) {
    return new OrderResponse(
        o.getId(),
        o.getStatus(),
        o.getTotal(),
        o.getCurrency(),
        JSON.readTree(o.getSnapshot() == null ? o.getItems() : o.getSnapshot()),
        o.getCreatedAt(),
        o.getFailureReason());
  }
}
