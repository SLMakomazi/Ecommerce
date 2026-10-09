package com.example.ecommerce.customer.dto;

import jakarta.validation.constraints.*;

public record CustomerRequest(
    @NotBlank @Size(max = 150) String name,
    @Size(max = 30) String phone,
    @Size(max = 1000) String address) {}
