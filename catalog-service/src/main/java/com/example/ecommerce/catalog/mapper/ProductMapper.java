package com.example.ecommerce.catalog.mapper;

import com.example.ecommerce.catalog.dto.ProductResponse;
import com.example.ecommerce.catalog.model.Product;

public final class ProductMapper {
  private ProductMapper() {}

  public static ProductResponse response(Product p) {
    return new ProductResponse(
        p.getId(),
        p.getSku(),
        p.getName(),
        p.getDescription(),
        p.getPrice(),
        p.getCurrency(),
        p.getAvailableStock());
  }
}
