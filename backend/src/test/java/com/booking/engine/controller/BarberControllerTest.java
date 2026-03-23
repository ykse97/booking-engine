package com.booking.engine.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.BarberResponseDto;
import com.booking.engine.service.BarberService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BarberControllerTest {

    private MockMvc mockMvc;
    private BarberService service;

    @BeforeEach
    void setup() {
        service = org.mockito.Mockito.mock(BarberService.class);
        BarberController controller = new BarberController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getAllReturns200() throws Exception {
        when(service.getAllBarbers()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/public/barbers"))
                .andExpect(status().isOk());
    }

    @Test
    void getByIdReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getBarberById(id)).thenReturn(BarberResponseDto.builder().id(id).build());
        mockMvc.perform(get("/api/v1/public/barbers/{id}", id))
                .andExpect(status().isOk());
    }
}
