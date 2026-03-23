package com.booking.engine.controller;

import com.booking.engine.properties.StripeProperties;
import com.booking.engine.service.BookingService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook controller for Stripe event callbacks.
 * Verifies signatures and synchronizes booking payment state.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeProperties stripeProperties;
    private final BookingService bookingService;

    /**
     * Handles Stripe webhook events.
     *
     * @param payload raw JSON payload
     * @param signature Stripe signature header
     * @return 200 on success, 400 on signature error
     */
    @PostMapping("/webhook")
    @Transactional
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        try {
            Event event = Webhook.constructEvent(payload, signature, stripeProperties.getWebhookSecret());
            processEvent(event);
            return ResponseEntity.ok("ok");
        } catch (SignatureVerificationException ex) {
            log.warn("Stripe webhook signature verification failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid signature");
        } catch (Exception ex) {
            log.error("Stripe webhook processing failed", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("webhook error");
        }
    }

    /*
     * Filters webhook events down to supported PaymentIntent callbacks and forwards
     * the extracted payment data to booking synchronization logic.
     *
     * @param event verified Stripe webhook event
     */
    private void processEvent(Event event) {
        String type = event.getType();
        if (event.getDataObjectDeserializer().getObject().isEmpty()) {
            log.warn("Skipping Stripe event without payload object type={}", type);
            return;
        }

        if (!isPaymentIntentEvent(type)) {
            log.info("Ignoring Stripe event type={}", type);
            return;
        }

        PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer().getObject().get();
        bookingService.syncStripePaymentIntentFromWebhook(intent.getId(), intent.getStatus(), type);
    }

    /*
     * Checks whether an incoming Stripe event type is one of the PaymentIntent
     * lifecycle events that should mutate booking payment state.
     *
     * @param type Stripe event type
     * @return {@code true} when the event should be processed
     */
    private boolean isPaymentIntentEvent(String type) {
        return "payment_intent.succeeded".equals(type)
                || "payment_intent.canceled".equals(type)
                || "payment_intent.payment_failed".equals(type);
    }

}
