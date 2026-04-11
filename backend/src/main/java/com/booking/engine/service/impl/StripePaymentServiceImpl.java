package com.booking.engine.service.impl;

import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.exception.PaymentProcessingException;
import com.booking.engine.properties.StripeProperties;
import com.booking.engine.security.SecurityAuditLogger;
import com.booking.engine.security.SensitiveLogSanitizer;
import com.booking.engine.service.StripePaymentService;
import com.booking.engine.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link StripePaymentService}.
 * Provides stripe payment related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripePaymentServiceImpl implements StripePaymentService {

    // ---------------------- Constants ----------------------

    private static final String PAYMENT_FAILURE_MESSAGE = "Payment could not be completed. Please try again or use a different payment method.";
    private static final String PAYMENT_STATUS_UNAVAILABLE_MESSAGE = "Unable to verify payment status right now. Please try again.";

    // ---------------------- Properties ----------------------

    private final StripeProperties stripeProperties;

    // ---------------------- Services ----------------------

    private final StripeClient stripeClient;

    private final SecurityAuditLogger securityAuditLogger;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public BookingCheckoutSessionResponseDto createAndConfirmPaymentWithConfirmationToken(
            BigDecimal amount,
            String customerEmail,
            String confirmationTokenId,
            Map<String, String> metadata) {
        try {
            log.info(
                    "event=stripe_payment_create action=confirmation_token_received confirmationTokenPresent={} amount={} customerEmailMask={} metadataKeys={}",
                    confirmationTokenId != null && !confirmationTokenId.isBlank(),
                    amount,
                    securityAuditLogger.maskEmail(customerEmail),
                    summarizeMetadataKeys(metadata));
            PaymentIntentCreateParams params = cardPaymentIntentParams(amount, customerEmail, metadata)
                    .setConfirm(true)
                    .setConfirmationToken(confirmationTokenId)
                    .build();
            PaymentIntent intent = stripeClient.createPaymentIntent(params);

            log.info(
                    "event=stripe_payment_create action=confirmation_token_success paymentIntentHash={} amount={} currency={} paymentStatus={}",
                    hashPaymentIntentForLogs(intent.getId()),
                    amount,
                    stripeProperties.getCurrency(),
                    intent.getStatus());

            return BookingCheckoutSessionResponseDto.builder()
                    .paymentIntentId(intent.getId())
                    .clientSecret(intent.getClientSecret())
                    .paymentStatus(intent.getStatus())
                    .build();
        } catch (StripeException ex) {
            String details = ex.getStripeError() != null && ex.getStripeError().getMessage() != null
                    ? ex.getStripeError().getMessage()
                    : ex.getMessage();
            log.warn("event=stripe_payment_create outcome=confirmation_token_failed reason={} details={}",
                    ex.getClass().getSimpleName(),
                    sanitizeLogDetails(details));
            throw new PaymentProcessingException(
                    PAYMENT_FAILURE_MESSAGE,
                    "Stripe payment via confirmation token failed: " + details,
                    ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String createAndConfirmPayment(
            BigDecimal amount,
            String customerEmail,
            String paymentMethodId,
            Map<String, String> metadata) {
        try {
            PaymentIntentCreateParams params = cardPaymentIntentParams(amount, customerEmail, metadata)
                    .setConfirm(true)
                    .setPaymentMethod(paymentMethodId)
                    .build();

            PaymentIntent intent = stripeClient.createPaymentIntent(params);

            if (!"succeeded".equals(intent.getStatus())) {
                log.warn(
                        "event=stripe_payment_create outcome=unexpected_status paymentIntentHash={} paymentStatus={}",
                        hashPaymentIntentForLogs(intent.getId()),
                        intent.getStatus());
                throw new PaymentProcessingException(PAYMENT_FAILURE_MESSAGE);
            }

            log.info("event=stripe_payment_create action=direct_success paymentIntentHash={} amount={} currency={}",
                    hashPaymentIntentForLogs(intent.getId()), amount, stripeProperties.getCurrency());

            return intent.getId();
        } catch (StripeException ex) {
            String details = ex.getStripeError() != null && ex.getStripeError().getMessage() != null
                    ? ex.getStripeError().getMessage()
                    : ex.getMessage();
            log.warn("event=stripe_payment_create outcome=direct_failed reason={} details={}",
                    ex.getClass().getSimpleName(),
                    sanitizeLogDetails(details));
            throw new PaymentProcessingException(
                    PAYMENT_FAILURE_MESSAGE,
                    "Stripe payment request failed: " + details,
                    ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPaymentIntentStatus(String paymentIntentId) {
        try {
            PaymentIntent intent = stripeClient.retrievePaymentIntent(paymentIntentId);
            return intent.getStatus();
        } catch (StripeException ex) {
            String details = ex.getStripeError() != null && ex.getStripeError().getMessage() != null
                    ? ex.getStripeError().getMessage()
                    : ex.getMessage();
            log.warn("event=stripe_payment_status_lookup outcome=failed paymentIntentHash={} reason={} details={}",
                    hashPaymentIntentForLogs(paymentIntentId),
                    ex.getClass().getSimpleName(),
                    sanitizeLogDetails(details));
            throw new PaymentProcessingException(
                    PAYMENT_STATUS_UNAVAILABLE_MESSAGE,
                    "Failed to retrieve Stripe PaymentIntent status: " + details,
                    ex);
        }
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Builds the shared PaymentIntent parameter set, including amount conversion
     * to cents, currency, receipt email, description, and booking metadata.
     *
     * @param amount booking amount in major currency units
     *
     * @param customerEmail customer receipt email
     *
     * @param metadata Stripe metadata map
     *
     * @return partially configured PaymentIntent builder
     */
    private PaymentIntentCreateParams.Builder basePaymentIntentParams(
            BigDecimal amount,
            String customerEmail,
            Map<String, String> metadata) {
        long amountInCents = amount.movePointRight(2).longValueExact();

        return PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(stripeProperties.getCurrency())
                .setReceiptEmail(customerEmail)
                .setDescription("Booking payment")
                .putAllMetadata(metadata);
    }

    /*
     * Extends the common PaymentIntent builder with card-specific payment method
     * configuration used by the checkout flows in this application.
     *
     * @param amount booking amount in major currency units
     *
     * @param customerEmail customer receipt email
     *
     * @param metadata Stripe metadata map
     *
     * @return card-enabled PaymentIntent builder
     */
    private PaymentIntentCreateParams.Builder cardPaymentIntentParams(
            BigDecimal amount,
            String customerEmail,
            Map<String, String> metadata) {
        return basePaymentIntentParams(amount, customerEmail, metadata)
                .addPaymentMethodType("card");
    }

    /**
     * Builds a safe summary of Stripe metadata keys for logging.
     */
    private Set<String> summarizeMetadataKeys(Map<String, String> metadata) {
        return metadata == null ? Set.of() : metadata.keySet();
    }

    /**
     * Sanitizes Stripe error details before writing them to logs.
     */
    private String sanitizeLogDetails(String details) {
        return SensitiveLogSanitizer.sanitizeForLogs(details);
    }

    /**
     * Hashes a Stripe PaymentIntent identifier for operational logs.
     */
    private String hashPaymentIntentForLogs(String paymentIntentId) {
        return SensitiveLogSanitizer.hashValue(paymentIntentId);
    }
}
