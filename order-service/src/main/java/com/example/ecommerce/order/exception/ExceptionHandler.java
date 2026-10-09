package com.example.ecommerce.order.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ExceptionHandler {
  @org.springframework.web.bind.annotation.ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> business(ApiException ex) {
    return ResponseEntity.status(ex.status())
        .body(ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage()));
  }

  @org.springframework.web.bind.annotation.ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex) {
    return ResponseEntity.badRequest()
        .body(
            ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed"));
  }

  @org.springframework.web.bind.annotation.ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException ex) {
    return ResponseEntity.status(409)
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Conflicting resource or request"));
  }
}
