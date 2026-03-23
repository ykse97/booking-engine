package com.booking.engine.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.BarberScheduleRequestDto;
import com.booking.engine.dto.BarberScheduleResponseDto;
import com.booking.engine.service.BarberScheduleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

class BarberScheduleAdminControllerTest {

    private MockMvc mockMvc;
    private BarberScheduleService service;
    private ObjectMapper objectMapper;
    private UUID barberId;

    @BeforeEach
    void setup() {
        service = org.mockito.Mockito.mock(BarberScheduleService.class);
        BarberScheduleAdminController controller = new BarberScheduleAdminController(service);
        objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        barberId = UUID.randomUUID();
    }

    @Test
    void getScheduleReturns200() throws Exception {
        BarberScheduleResponseDto dto = BarberScheduleResponseDto.builder()
                .id(UUID.randomUUID())
                .workingDate(LocalDate.now())
                .workingDay(true)
                .build();
        when(service.getSchedule(barberId, null, null)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/admin/barbers/{id}/schedule", barberId))
                .andExpect(status().isOk());
    }

    @Test
    void upsertDayReturns204() throws Exception {
        BarberScheduleRequestDto req = BarberScheduleRequestDto.builder()
                .workingDate(LocalDate.now())
                .workingDay(true)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .breakStartTime(LocalTime.of(13, 0))
                .breakEndTime(LocalTime.of(14, 0))
                .build();

        mockMvc.perform(put("/api/v1/admin/barbers/{id}/schedule", barberId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }
}
