package com.booking.engine.service.impl;

import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.exception.PaymentProcessingException;
import com.booking.engine.properties.StripeProperties;
import com.booking.engine.service.StripePaymentService;
import com.booking.engine.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stripe payment service implementation based on PaymentIntent API.
 * Charges the booking amount immediately and keeps booking review on the
 * application side.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripePaymentServiceImpl implements StripePaymentService {

    // ---------------------- Properties ----------------------

    private final StripeProperties stripeProperties;

    // ---------------------- Clients ----------------------

    private final StripeClient stripeClient;

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
            log.info("STRIPE_RUNTIME_CONFIRMATION_TOKEN_RECEIVED confirmationTokenIdPrefix={}, amount={}, customerEmail={}, metadata={}",
                    confirmationTokenId != null && confirmationTokenId.length() > 8
                            ? confirmationTokenId.substring(0, 8)
                            : confirmationTokenId,
                    amount,
                    customerEmail,
                    metadata);
            PaymentIntentCreateParams params = cardPaymentIntentParams(amount, customerEmail, metadata)
                    .setConfirm(true)
                    .setConfirmationToken(confirmationTokenId)
                    .build();
            PaymentIntent intent = stripeClient.createPaymentIntent(params);

            log.info("Stripe booking payment created via confirmation token paymentIntentId={}, amount={}, currency={}, status={}",
                    intent.getId(), amount, stripeProperties.getCurrency(), intent.getStatus());

            return BookingCheckoutSessionResponseDto.builder()
                    .paymentIntentId(intent.getId())
                    .clientSecret(intent.getClientSecret())
                    .paymentStatus(intent.getStatus())
                    .build();
        } catch (StripeException ex) {
            String details = ex.getStripeError() != null && ex.getStripeError().getMessage() != null
                    ? ex.getStripeError().getMessage()
                    : ex.getMessage();
            log.warn("Stripe payment via confirmation token failed: {}", details);
            throw new PaymentProcessingException("Stripe payment failed: " + details, ex);
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
                throw new PaymentProcessingException(
                        "Stripe payment was not completed. PaymentIntent status: " + intent.getStatus());
            }

            log.info("Stripe booking payment created paymentIntentId={}, amount={}, currency={}",
                    intent.getId(), amount, stripeProperties.getCurrency());

            return intent.getId();
        } catch (StripeException ex) {
            String details = ex.getStripeError() != null && ex.getStripeError().getMessage() != null
                    ? ex.getStripeError().getMessage()
                    : ex.getMessage();
            log.warn("Stripe payment request failed: {}", details);
            throw new PaymentProcessingException("Stripe payment request failed: " + details, ex);
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
            throw new PaymentProcessingException("Failed to retrieve Stripe PaymentIntent status: " + details, ex);
        }
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Builds the shared PaymentIntent parameter set, including amount conversion
     * to cents, currency, receipt email, description, and booking metadata.
     *
     * @param amount booking amount in major currency units
     * @param customerEmail customer receipt email
     * @param metadata Stripe metadata map
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
     * @param customerEmail customer receipt email
     * @param metadata Stripe metadata map
     * @return card-enabled PaymentIntent builder
     */
    private PaymentIntentCreateParams.Builder cardPaymentIntentParams(
            BigDecimal amount,
            String customerEmail,
            Map<String, String> metadata) {
        return basePaymentIntentParams(amount, customerEmail, metadata)
                .addPaymentMethodType("card");
    }
}
