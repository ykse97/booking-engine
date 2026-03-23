package com.booking.engine.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.BarberSchedulePeriodDayRequestDto;
import com.booking.engine.dto.BarberSchedulePeriodRequestDto;
import com.booking.engine.dto.BarberSchedulePeriodResponseDto;
import com.booking.engine.service.BarberScheduleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BarberSchedulePeriodAdminControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private BarberScheduleService service;

    @BeforeEach
    void setup() {
        service = org.mockito.Mockito.mock(BarberScheduleService.class);
        BarberSchedulePeriodAdminController controller = new BarberSchedulePeriodAdminController(service);
        objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void getPeriodReturns200() throws Exception {
        when(service.getPeriodSettings()).thenReturn(BarberSchedulePeriodResponseDto.builder()
                .startDate(LocalDate.of(2026, 4, 3))
                .endDate(LocalDate.of(2026, 4, 9))
                .applyToAllBarbers(true)
                .days(List.of())
                .build());

        mockMvc.perform(get("/api/v1/admin/barbers/schedule/period"))
                .andExpect(status().isOk());
    }

    @Test
    void upsertPeriodReturns200() throws Exception {
        BarberSchedulePeriodRequestDto request = BarberSchedulePeriodRequestDto.builder()
                .startDate(LocalDate.of(2026, 4, 3))
                .endDate(LocalDate.of(2026, 4, 9))
                .applyToAllBarbers(true)
                .days(Arrays.stream(DayOfWeek.values())
                        .map(day -> BarberSchedulePeriodDayRequestDto.builder()
                                .dayOfWeek(day)
                                .workingDay(true)
                                .openTime(LocalTime.of(9, 0))
                                .closeTime(LocalTime.of(18, 0))
                                .breakStartTime(LocalTime.of(13, 0))
                                .breakEndTime(LocalTime.of(14, 0))
                                .build())
                        .toList())
                .build();

        when(service.upsertPeriod(org.mockito.ArgumentMatchers.any())).thenReturn(BarberSchedulePeriodResponseDto.builder()
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .applyToAllBarbers(request.getApplyToAllBarbers())
                .days(request.getDays())
                .build());

        mockMvc.perform(put("/api/v1/admin/barbers/schedule/period")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
