package com.example.ecommerce.order.dto;

import jakarta.validation.constraints.*;

public record CheckoutRequest(
    @NotBlank @Size(max = 1000) String address,
    @NotBlank @Pattern(regexp = "sandbox-success|sandbox-decline") String paymentToken) {}
