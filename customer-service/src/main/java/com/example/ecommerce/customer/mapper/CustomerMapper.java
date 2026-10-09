package com.example.ecommerce.customer.mapper;

import com.example.ecommerce.customer.dto.CustomerResponse;
import com.example.ecommerce.customer.model.Customer;

public final class CustomerMapper {
  private CustomerMapper() {}

  public static CustomerResponse response(Customer c) {
    return new CustomerResponse(
        c.getId(), c.getName(), c.getPhone(), c.getAddress(), c.getUpdatedAt());
  }
}
