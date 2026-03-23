package com.booking.engine.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.exception.GlobalExceptionHandler;
import com.booking.engine.service.BookingService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Web integration tests for {@link BookingController}.
 */
class BookingControllerWebMvcTest {

    private MockMvc mockMvc;

    @Mock
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        BookingController controller = new BookingController(bookingService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createBookingShouldReturn201WhenPayloadIsValid() throws Exception {
        UUID bookingId = UUID.randomUUID();
        BookingResponseDto response = BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.CONFIRMED)
                .stripePaymentIntentId("pi_test")
                .build();

        when(bookingService.create(any(BookingRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/public/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.stripePaymentIntentId").value("pi_test"));
    }

    @Test
    void createBookingShouldReturn400WhenPaymentMethodIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/public/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestJsonWithoutPaymentMethod()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.paymentMethodId").exists());
    }

    @Test
    void holdBookingShouldReturn201WhenPayloadIsValid() throws Exception {
        UUID bookingId = UUID.randomUUID();
        BookingResponseDto response = BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.PENDING)
                .build();

        when(bookingService.holdSlot(any(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/public/bookings/hold")
                        .header("X-Booking-Device-Id", "device-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validHoldRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void prepareHeldBookingCheckoutShouldReturn200WhenPayloadIsValid() throws Exception {
        UUID bookingId = UUID.randomUUID();
        BookingCheckoutSessionResponseDto response = BookingCheckoutSessionResponseDto.builder()
                .paymentIntentId("pi_checkout")
                .clientSecret("pi_checkout_secret_123")
                .paymentStatus("succeeded")
                .build();

        when(bookingService.prepareHeldBookingCheckout(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/public/bookings/{id}/checkout", bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCheckoutRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentIntentId").value("pi_checkout"))
                .andExpect(jsonPath("$.clientSecret").value("pi_checkout_secret_123"))
                .andExpect(jsonPath("$.paymentStatus").value("succeeded"));
    }

    @Test
    void confirmHeldBookingShouldReturn400WhenPaymentIntentIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/public/bookings/{id}/confirm", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidConfirmRequestJsonWithoutPaymentIntent()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.paymentIntentId").exists());
    }

    @Test
    void getBookingByIdShouldReturn200() throws Exception {
        UUID bookingId = UUID.randomUUID();
        BookingResponseDto response = BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.PENDING)
                .build();

        when(bookingService.getBookingById(bookingId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/public/bookings/{id}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    private String validRequestJson() {
        return """
                {
                  "barberId": "11111111-1111-1111-1111-111111111111",
                  "treatmentId": "22222222-2222-2222-2222-222222222222",
                  "bookingDate": "2030-01-15",
                  "startTime": "10:00:00",
                  "endTime": "10:05:00",
                  "paymentMethodId": "pm_card_visa",
                  "customer": {
                    "name": "John Doe",
                    "email": "john@example.com",
                    "phone": "+353870000000"
                  }
                }
                """;
    }

    private String validHoldRequestJson() {
        return """
                {
                  "barberId": "11111111-1111-1111-1111-111111111111",
                  "treatmentId": "22222222-2222-2222-2222-222222222222",
                  "bookingDate": "2030-01-15",
                  "startTime": "10:00:00",
                  "endTime": "11:00:00"
                }
                """;
    }

    private String invalidRequestJsonWithoutPaymentMethod() {
        return """
                {
                  "barberId": "11111111-1111-1111-1111-111111111111",
                  "treatmentId": "22222222-2222-2222-2222-222222222222",
                  "bookingDate": "2030-01-15",
                  "startTime": "10:00:00",
                  "endTime": "10:05:00",
                  "paymentMethodId": " ",
                  "customer": {
                    "name": "John Doe",
                    "email": "john@example.com",
                    "phone": "+353870000000"
                  }
                }
                """;
    }

    private String validCheckoutRequestJson() {
        return """
                {
                  "customer": {
                    "name": "John Doe",
                    "email": "john@example.com",
                    "phone": "+353870000000"
                  },
                  "confirmationTokenId": "ctoken_123"
                }
                """;
    }

    private String invalidConfirmRequestJsonWithoutPaymentIntent() {
        return """
                {
                  "paymentIntentId": " "
                }
                """;
    }
}
