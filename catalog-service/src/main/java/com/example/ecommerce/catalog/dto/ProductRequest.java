package com.example.ecommerce.catalog.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProductRequest(
    @NotBlank @Size(max = 80) String sku,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 2000) String description,
    @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 7, fraction = 2)
        BigDecimal price,
    @NotBlank @Pattern(regexp = "ZAR") String currency,
    @Min(0) @Max(1000000) int stock) {}
