package com.example.ecommerce.customer.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
    UUID id, String name, String phone, String address, Instant updatedAt) {}
