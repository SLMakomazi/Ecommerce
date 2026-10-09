package com.example.ecommerce.catalog.repository;

import com.example.ecommerce.catalog.model.Reservation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {}
