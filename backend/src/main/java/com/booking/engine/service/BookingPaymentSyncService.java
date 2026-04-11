package com.booking.engine.service;

import java.util.Map;

/**
 * Service contract for booking payment sync operations.
 * Defines booking payment sync related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
public interface BookingPaymentSyncService {

    /**
     * Synchronizes stripe payment intent from webhook.
     *
     * @param paymentIntentId payment intent identifier
     * @param paymentStatus payment status value
     * @param eventType event type value
     */
    default void syncStripePaymentIntentFromWebhook(String paymentIntentId, String paymentStatus, String eventType) {
        syncStripePaymentIntentFromWebhook(paymentIntentId, paymentStatus, eventType, Map.of());
    }

    /**
     * Synchronizes stripe payment intent from webhook.
     *
     * @param paymentIntentId payment intent identifier
     * @param paymentStatus payment status value
     * @param eventType event type value
     * @param String string value
     * @param metadata metadata values
     */
    void syncStripePaymentIntentFromWebhook(
            String paymentIntentId,
            String paymentStatus,
            String eventType,
            Map<String, String> metadata);
}
