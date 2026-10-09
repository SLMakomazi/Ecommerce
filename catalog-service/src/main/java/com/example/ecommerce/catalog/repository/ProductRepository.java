package com.example.ecommerce.catalog.repository;

import com.example.ecommerce.catalog.model.Product;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ProductRepository extends JpaRepository<Product, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Product p where p.id = :id")
  Optional<Product> lockById(UUID id);
}
