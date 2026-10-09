package com.example.ecommerce.order.repository;

import com.example.ecommerce.order.model.Cart;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, UUID> {}
