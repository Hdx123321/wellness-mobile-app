package com.wellnessmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point for the WellnessMate backend.
 *
 * @author TODO(team member)
 */
@SpringBootApplication
public class WellnessBackendApplication {

  private WellnessBackendApplication() {
  }

  /**
   * Starts the Spring Boot application.
   *
   * @param args command-line arguments
   * @author TODO(team member)
   */
  public static void main(String[] args) {
    SpringApplication.run(WellnessBackendApplication.class, args);
  }
}

