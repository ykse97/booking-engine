package com.booking.engine.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.properties.StripeProperties;
import com.booking.engine.service.BookingPaymentSyncService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    @Mock
    private StripeProperties stripeProperties;
    @Mock
    private BookingPaymentSyncService bookingService;

    @InjectMocks
    private StripeWebhookController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(stripeProperties.getWebhookSecret()).thenReturn("whsec_test");
    }

    @Test
    void handleWebhookDelegatesSucceededPaymentIntentSync() throws Exception {
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn("pi_1");
        when(intent.getStatus()).thenReturn("succeeded");
        when(intent.getMetadata()).thenReturn(Map.of("slotHoldId", "hold-1"));

        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getType()).thenReturn("payment_intent.succeeded");
        when(deserializer.getObject()).thenReturn(Optional.of(intent));
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

            mockMvc.perform(post("/webhook")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Stripe-Signature", "t=sig")
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ok"));
        }

        verify(bookingService).syncStripePaymentIntentFromWebhook(
                "pi_1",
                "succeeded",
                "payment_intent.succeeded",
                Map.of("slotHoldId", "hold-1"));
    }

    @Test
    void handleWebhookReturns400OnSignatureError() throws Exception {
        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(any(), any(), any()))
                    .thenThrow(new SignatureVerificationException("bad sig", "header"));

            mockMvc.perform(post("/webhook")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Stripe-Signature", "bad")
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("invalid signature"));
        }
    }
}
