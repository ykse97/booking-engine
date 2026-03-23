package com.booking.engine.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.engine.dto.BarberRequestDto;
import com.booking.engine.dto.BarberResponseDto;
import com.booking.engine.dto.ReorderRequestDto;
import com.booking.engine.service.BarberService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminBarberControllerTest {

    private MockMvc mockMvc;
    private BarberService barberService;
    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        barberService = org.mockito.Mockito.mock(BarberService.class);
        AdminBarberController controller = new AdminBarberController(barberService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        mapper = new ObjectMapper();
    }

    @Test
    void createReturns201() throws Exception {
        BarberRequestDto req = BarberRequestDto.builder()
                .name("B1")
                .role("Senior Barber")
                .displayOrder(0)
                .build();
        when(barberService.createBarber(req)).thenReturn(BarberResponseDto.builder().id(UUID.randomUUID()).build());

        mockMvc.perform(post("/api/v1/admin/barbers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void updateReturns204() throws Exception {
        BarberRequestDto req = BarberRequestDto.builder()
                .name("B1")
                .role("Senior Barber")
                .displayOrder(0)
                .build();
        UUID id = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/admin/barbers/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
        verify(barberService).updateBarber(id, req);
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/admin/barbers/{id}", id))
                .andExpect(status().isNoContent());
        verify(barberService).deleteBarber(id);
    }

    @Test
    void reorderReturns204() throws Exception {
        ReorderRequestDto req = new ReorderRequestDto(UUID.randomUUID(), UUID.randomUUID());
        mockMvc.perform(post("/api/v1/admin/barbers/reorder")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
        verify(barberService).reorderBarbers(req.getId1(), req.getId2());
    }
}
