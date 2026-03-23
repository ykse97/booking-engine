package com.booking.engine.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.BookingConfirmationRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

class BookingControllerTest {

    private MockMvc mockMvc;
    private BookingService bookingService;
    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        bookingService = org.mockito.Mockito.mock(BookingService.class);
        BookingController controller = new BookingController(bookingService);
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void getByIdReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(bookingService.getBookingById(id)).thenReturn(BookingResponseDto.builder().id(id).build());
        mockMvc.perform(get("/api/v1/public/bookings/{id}", id))
                .andExpect(status().isOk());
    }

    @Test
    void createReturns201() throws Exception {
        BookingRequestDto req = BookingRequestDto.builder()
                .barberId(UUID.randomUUID())
                .treatmentId(UUID.randomUUID())
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .paymentMethodId("pm")
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("n").email("e@e.com").phone("1").build())
                .build();
        when(bookingService.create(req)).thenReturn(BookingResponseDto.builder()
                .id(UUID.randomUUID()).status(BookingStatus.CONFIRMED).build());

        mockMvc.perform(post("/api/v1/public/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void holdReturns201() throws Exception {
        BookingHoldRequestDto req = BookingHoldRequestDto.builder()
                .barberId(UUID.randomUUID())
                .treatmentId(UUID.randomUUID())
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .build();

        when(bookingService.holdSlot(req, "203.0.113.10", "device-123")).thenReturn(BookingResponseDto.builder()
                .id(UUID.randomUUID()).status(BookingStatus.PENDING).build());

        mockMvc.perform(post("/api/v1/public/bookings/hold")
                        .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
                        .header("X-Booking-Device-Id", "device-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void confirmHoldReturns200() throws Exception {
        UUID bookingId = UUID.randomUUID();
        BookingConfirmationRequestDto req = BookingConfirmationRequestDto.builder()
                .paymentIntentId("pi_auth")
                .build();

        when(bookingService.confirmHeldBooking(bookingId, req)).thenReturn(BookingResponseDto.builder()
                .id(bookingId).status(BookingStatus.PENDING).build());

        mockMvc.perform(post("/api/v1/public/bookings/{id}/confirm", bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void prepareCheckoutReturns200() throws Exception {
        UUID bookingId = UUID.randomUUID();
        BookingCheckoutSessionRequestDto req = BookingCheckoutSessionRequestDto.builder()
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("n")
                        .email("e@e.com")
                        .phone("1")
                        .build())
                .confirmationTokenId("ctoken_123")
                .build();

        when(bookingService.prepareHeldBookingCheckout(bookingId, req))
                .thenReturn(BookingCheckoutSessionResponseDto.builder()
                        .paymentIntentId("pi_checkout")
                        .clientSecret("pi_checkout_secret")
                        .paymentStatus("succeeded")
                        .build());

        mockMvc.perform(post("/api/v1/public/bookings/{id}/checkout", bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/public/bookings/{id}", id))
                .andExpect(status().isNoContent());
        verify(bookingService).cancelBooking(id);
    }
}
