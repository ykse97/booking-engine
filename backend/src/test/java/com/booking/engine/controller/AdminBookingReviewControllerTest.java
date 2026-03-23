package com.booking.engine.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.AdminBookingListResponseDto;
import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.BookingBlacklistEntryRequestDto;
import com.booking.engine.dto.BookingBlacklistEntryResponseDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.service.BookingBlacklistService;
import com.booking.engine.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminBookingReviewControllerTest {

    private MockMvc mockMvc;
    private BookingService bookingService;
    private BookingBlacklistService bookingBlacklistService;
    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        bookingService = org.mockito.Mockito.mock(BookingService.class);
        bookingBlacklistService = org.mockito.Mockito.mock(BookingBlacklistService.class);
        AdminBookingReviewController controller = new AdminBookingReviewController(bookingService, bookingBlacklistService);
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void getAdminBookingsReturns200() throws Exception {
        when(bookingService.getAdminBookings("john")).thenReturn(AdminBookingListResponseDto.builder()
                .confirmedCount(4)
                .filteredCount(1)
                .bookings(List.of(
                        BookingResponseDto.builder()
                                .id(UUID.randomUUID())
                                .customerName("John Doe")
                                .status(BookingStatus.CONFIRMED)
                                .build()))
                .build());

        mockMvc.perform(get("/api/v1/admin/bookings").param("search", "john"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedCount").value(4))
                .andExpect(jsonPath("$.filteredCount").value(1))
                .andExpect(jsonPath("$.bookings[0].customerName").value("John Doe"));
    }

    @Test
    void createAdminBookingReturns200() throws Exception {
        UUID barberId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        AdminBookingCreateRequestDto request = AdminBookingCreateRequestDto.builder()
                .barberId(barberId)
                .treatmentId(treatmentId)
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .customerName("Phone Client")
                .customerPhone("+353831234567")
                .build();

        when(bookingService.createAdminBooking(request)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.CONFIRMED)
                .customerName("Phone Client")
                .customerPhone("+353831234567")
                .build());

        mockMvc.perform(post("/api/v1/admin/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.customerName").value("Phone Client"))
                .andExpect(jsonPath("$.customerPhone").value("+353831234567"));

        verify(bookingService).createAdminBooking(request);
    }

    @Test
    void cancelBookingReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(bookingService.cancelBookingByAdmin(id)).thenReturn(BookingResponseDto.builder()
                .id(id)
                .status(BookingStatus.CANCELLED)
                .build());

        mockMvc.perform(post("/api/v1/admin/bookings/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(bookingService).cancelBookingByAdmin(id);
    }

    @Test
    void getBlacklistEntriesReturns200() throws Exception {
        UUID entryId = UUID.randomUUID();
        when(bookingBlacklistService.getActiveEntries()).thenReturn(List.of(
                BookingBlacklistEntryResponseDto.builder()
                        .id(entryId)
                        .phone("+353831234567")
                        .reason("Chargeback abuse")
                        .build()));

        mockMvc.perform(get("/api/v1/admin/bookings/blacklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(entryId.toString()))
                .andExpect(jsonPath("$[0].phone").value("+353831234567"))
                .andExpect(jsonPath("$[0].reason").value("Chargeback abuse"));
    }

    @Test
    void createBlacklistEntryReturns200() throws Exception {
        BookingBlacklistEntryRequestDto request = BookingBlacklistEntryRequestDto.builder()
                .email("blocked@example.com")
                .phone("+353831234567")
                .reason("Repeated no-shows")
                .build();
        UUID entryId = UUID.randomUUID();

        when(bookingBlacklistService.createEntry(request)).thenReturn(BookingBlacklistEntryResponseDto.builder()
                .id(entryId)
                .email("blocked@example.com")
                .phone("+353831234567")
                .reason("Repeated no-shows")
                .build());

        mockMvc.perform(post("/api/v1/admin/bookings/blacklist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entryId.toString()))
                .andExpect(jsonPath("$.email").value("blocked@example.com"))
                .andExpect(jsonPath("$.phone").value("+353831234567"));
    }

    @Test
    void deleteBlacklistEntryReturns204() throws Exception {
        UUID entryId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/admin/bookings/blacklist/{id}", entryId))
                .andExpect(status().isNoContent());

        verify(bookingBlacklistService).deleteEntry(entryId);
    }
}
