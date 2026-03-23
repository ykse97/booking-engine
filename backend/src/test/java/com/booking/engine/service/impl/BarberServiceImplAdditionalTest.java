package com.booking.engine.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.BarberRequestDto;
import com.booking.engine.entity.BarberEntity;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.mapper.BarberMapper;
import com.booking.engine.repository.BarberRepository;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.HairSalonHoursRepository;
import com.booking.engine.service.DisplayOrderService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Additional unit tests for {@link BarberServiceImpl}.
 *
 * @author Yehor
 * @version 2.0
 * @since March 2026
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BarberServiceImplAdditionalTest {

    @Mock
    private BarberRepository barberRepository;

    @Mock
    private HairSalonHoursRepository hairSalonHoursRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BarberMapper mapper;

    @Mock
    private DisplayOrderService displayOrderService;

    @InjectMocks
    private BarberServiceImpl service;

    @Test
    void deleteBarberThrowsWhenActiveBookingsExist() {
        UUID barberId = UUID.randomUUID();
        BarberEntity barber = new BarberEntity();
        barber.setActive(true);
        barber.setDisplayOrder(0);

        when(barberRepository.findByIdAndActiveTrue(barberId)).thenReturn(Optional.of(barber));
        when(bookingRepository.existsByBarberIdAndStatusIn(eq(barberId), anyList())).thenReturn(true);

        assertThatThrownBy(() -> service.deleteBarber(barberId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active bookings");

        verify(displayOrderService).lockActiveOrderingScope(barberRepository);
    }

    @Test
    void getBarberByIdThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(barberRepository.findByIdAndActiveTrue(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBarberById(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void createBarberValidatesDisplayOrderUpperBound() {
        BarberRequestDto request = BarberRequestDto.builder()
                .name("B")
                .role("Senior Barber")
                .displayOrder(5)
                .build();

        when(displayOrderService.resolveDisplayOrder(request.getDisplayOrder(), barberRepository))
                .thenThrow(new IllegalArgumentException("Display order cannot be greater than 1"));

        assertThatThrownBy(() -> service.createBarber(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Display order cannot be greater");

        verify(displayOrderService).lockActiveOrderingScope(barberRepository);
    }

    @Test
    void updateBarberShiftsDownWhenMovingForward() {
        UUID barberId = UUID.randomUUID();

        BarberEntity barber = new BarberEntity();
        barber.setActive(true);
        barber.setDisplayOrder(0);

        when(barberRepository.findByIdAndActiveTrue(barberId)).thenReturn(Optional.of(barber));
        when(displayOrderService.resolveDisplayOrderForUpdate(2, 0, barberRepository)).thenReturn(2);

        BarberRequestDto request = BarberRequestDto.builder()
                .role("Master Barber")
                .displayOrder(2)
                .build();

        service.updateBarber(barberId, request);

        assertThat(barber.getDisplayOrder()).isEqualTo(2);

        verify(displayOrderService).lockActiveOrderingScope(barberRepository);
        verify(displayOrderService).resolveDisplayOrderForUpdate(2, 0, barberRepository);
        verify(displayOrderService).moveDisplayOrder(0, 2, barberRepository);
        verify(mapper).updateFromDto(request, barber);
    }

    @Test
    void updateBarberShiftsUpWhenMovingBackward() {
        UUID barberId = UUID.randomUUID();

        BarberEntity barber = new BarberEntity();
        barber.setActive(true);
        barber.setDisplayOrder(2);

        when(barberRepository.findByIdAndActiveTrue(barberId)).thenReturn(Optional.of(barber));
        when(displayOrderService.resolveDisplayOrderForUpdate(0, 2, barberRepository)).thenReturn(0);

        BarberRequestDto request = BarberRequestDto.builder()
                .role("Master Barber")
                .displayOrder(0)
                .build();

        service.updateBarber(barberId, request);

        assertThat(barber.getDisplayOrder()).isEqualTo(0);

        verify(displayOrderService).lockActiveOrderingScope(barberRepository);
        verify(displayOrderService).resolveDisplayOrderForUpdate(0, 2, barberRepository);
        verify(displayOrderService).moveDisplayOrder(2, 0, barberRepository);
        verify(mapper).updateFromDto(request, barber);
    }

    @Test
    void updateBarberValidatesDisplayOrderUpperBoundForUpdate() {
        UUID barberId = UUID.randomUUID();

        BarberEntity barber = new BarberEntity();
        barber.setActive(true);
        barber.setDisplayOrder(1);

        when(barberRepository.findByIdAndActiveTrue(barberId)).thenReturn(Optional.of(barber));
        when(displayOrderService.resolveDisplayOrderForUpdate(5, 1, barberRepository))
                .thenThrow(new IllegalArgumentException("Display order cannot be greater than 1"));

        BarberRequestDto request = BarberRequestDto.builder()
                .role("Master Barber")
                .displayOrder(5)
                .build();

        assertThatThrownBy(() -> service.updateBarber(barberId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Display order cannot be greater");

        verify(displayOrderService).lockActiveOrderingScope(barberRepository);
    }
}
