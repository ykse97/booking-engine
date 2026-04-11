package com.booking.engine.controller;

import com.booking.engine.properties.StripeProperties;
import com.booking.engine.service.BookingPaymentSyncService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final BookingPaymentSyncService bookingService;

    /**
     * Handles Stripe webhook events.
     *
     * @param payload raw JSON payload
     * @param signature Stripe signature header
     * @return 200 on success, 400 on signature error
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        try {
            Event event = Webhook.constructEvent(payload, signature, stripeProperties.getWebhookSecret());
            processEvent(event);
            return ResponseEntity.ok("ok");
        } catch (SignatureVerificationException ex) {
            log.warn("event=stripe_webhook outcome=invalid_signature reason={}", ex.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid signature");
        } catch (Exception ex) {
            log.error("event=stripe_webhook outcome=processing_failed", ex);
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
            log.warn("event=stripe_webhook action=skip reason=missing_payload eventType={}", type);
            return;
        }

        if (!isPaymentIntentEvent(type)) {
            log.info("event=stripe_webhook action=ignore eventType={}", type);
            return;
        }

        PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer().getObject().get();
        bookingService.syncStripePaymentIntentFromWebhook(intent.getId(), intent.getStatus(), type, intent.getMetadata());
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
