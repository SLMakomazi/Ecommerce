package com.example.ecommerce.customer.controller;

import com.example.ecommerce.customer.dto.*;
import com.example.ecommerce.customer.service.CustomerService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers/me")
public class CustomerController {
  private final CustomerService service;

  public CustomerController(CustomerService service) {
    this.service = service;
  }

  @GetMapping
  public CustomerResponse get(@AuthenticationPrincipal Jwt jwt) {
    return service.get(UUID.fromString(jwt.getSubject()));
  }

  @PutMapping
  public CustomerResponse put(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CustomerRequest request) {
    return service.save(UUID.fromString(jwt.getSubject()), request);
  }
}
