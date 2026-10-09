package com.example.ecommerce.customer.service;

import com.example.ecommerce.customer.dto.*;
import com.example.ecommerce.customer.exception.ApiException;
import com.example.ecommerce.customer.mapper.CustomerMapper;
import com.example.ecommerce.customer.model.Customer;
import com.example.ecommerce.customer.repository.CustomerRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
  private final CustomerRepository repository;

  public CustomerService(CustomerRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public CustomerResponse get(UUID id) {
    return CustomerMapper.response(
        repository
            .findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Profile not created")));
  }

  @Transactional
  public CustomerResponse save(UUID id, CustomerRequest request) {
    var c = repository.findById(id).orElseGet(Customer::new);
    c.setId(id);
    c.setName(request.name());
    c.setPhone(request.phone());
    c.setAddress(request.address());
    c.setUpdatedAt(Instant.now());
    return CustomerMapper.response(repository.save(c));
  }
}
