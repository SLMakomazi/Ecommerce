package com.example.ecommerce.customer.repository;

import com.example.ecommerce.customer.model.Customer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {}
