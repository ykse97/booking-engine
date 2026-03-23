package com.booking.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for Booking Engine Spring Boot application.
 * Enables scheduling and configuration properties binding.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class BookingEngineApplication {

    /**
     * Application bootstrap method.
     *
     * @param args runtime arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(BookingEngineApplication.class, args);
    }

}
