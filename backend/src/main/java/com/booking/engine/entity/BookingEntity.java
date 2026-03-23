package com.booking.engine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.math.BigDecimal;

/**
 * Entity representing a customer booking.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Entity
@Table(name = "booking")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BookingEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barber_id", nullable = false)
    private BarberEntity barber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treatment_id", nullable = false)
    private TreatmentEntity treatment;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @Column(name = "customer_phone", length = 50)
    private String customerPhone;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private BookingStatus status;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    @Column(name = "stripe_payment_status", length = 100)
    private String stripePaymentStatus;

    @Column(name = "hold_amount", precision = 10, scale = 2)
    private BigDecimal holdAmount;

    @Column(name = "hold_client_ip", length = 64)
    private String holdClientIp;

    @Column(name = "hold_client_device_id", length = 128)
    private String holdClientDeviceId;

    @Column(name = "payment_captured_at")
    private LocalDateTime paymentCapturedAt;

    @Column(name = "payment_released_at")
    private LocalDateTime paymentReleasedAt;
}
