package com.example.ecommerce.migrations;

import org.flywaydb.core.Flyway;

/** Native-platform, one-shot migration job for the Node.js service databases. */
public final class Migrate {
  private Migrate() {}

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    return value;
  }

  public static void main(String[] args) {
    Flyway.configure()
        .dataSource(required("FLYWAY_URL"), required("FLYWAY_USER"), required("FLYWAY_PASSWORD"))
        .locations("filesystem:/flyway/sql")
        .connectRetries(30)
        .load()
        .migrate();
  }
}
