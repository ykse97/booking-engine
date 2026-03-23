package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.BarberRequestDto;
import com.booking.engine.dto.BarberResponseDto;
import com.booking.engine.entity.BarberEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.mapper.BarberMapper;
import com.booking.engine.repository.BarberRepository;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.service.DisplayOrderService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link BarberServiceImpl}.
 *
 * @author Yehor
 * @version 2.0
 * @since March 2026
 */
@ExtendWith(MockitoExtension.class)
class BarberServiceImplTest {

    @Mock
    private BarberRepository barberRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BarberMapper mapper;

    @Mock
    private DisplayOrderService displayOrderService;

    @InjectMocks
    private BarberServiceImpl barberService;

    @Test
    void createBarberShouldCreateWithoutScheduleSeed() {
        UUID barberId = UUID.randomUUID();

        BarberRequestDto request = BarberRequestDto.builder()
                .name("Barber One")
                .role("Senior Barber")
                .bio("Senior barber")
                .photoUrl("https://example.com/b1.jpg")
                .build();

        BarberEntity toSave = new BarberEntity();
        BarberEntity saved = new BarberEntity();
        saved.setId(barberId);
        saved.setDisplayOrder(0);

        BarberResponseDto response = BarberResponseDto.builder()
                .id(barberId)
                .build();

        when(displayOrderService.resolveDisplayOrder(request.getDisplayOrder(), barberRepository))
                .thenReturn(0);
        when(mapper.toEntity(request)).thenReturn(toSave);
        when(barberRepository.save(toSave)).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(response);

        barberService.createBarber(request);

        verify(displayOrderService).lockActiveOrderingScope(barberRepository);
        verify(displayOrderService).resolveDisplayOrder(request.getDisplayOrder(), barberRepository);
        verify(displayOrderService).shiftDisplayOrders(0, barberRepository);
        verify(barberRepository).save(toSave);
    }

    @Test
    void deleteBarberShouldThrowWhenActiveBookingsExist() {
        UUID barberId = UUID.randomUUID();
        BarberEntity barber = new BarberEntity();
        barber.setId(barberId);
        barber.setDisplayOrder(0);
        barber.setActive(true);

        when(barberRepository.findByIdAndActiveTrue(barberId)).thenReturn(Optional.of(barber));
        when(bookingRepository.existsByBarberIdAndStatusIn(
                barberId,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)))
                .thenReturn(true);

        assertThrows(IllegalStateException.class, () -> barberService.deleteBarber(barberId));

        verify(displayOrderService).lockActiveOrderingScope(barberRepository);
        verify(barberRepository, never()).delete(any());
    }

    @Test
    void deleteBarberShouldDeleteWhenNoActiveBookingsExist() {
        UUID barberId = UUID.randomUUID();
        BarberEntity barber = new BarberEntity();
        barber.setId(barberId);
        barber.setDisplayOrder(0);
        barber.setActive(true);

        when(barberRepository.findByIdAndActiveTrue(barberId)).thenReturn(Optional.of(barber));
        when(bookingRepository.existsByBarberIdAndStatusIn(
                barberId,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)))
                .thenReturn(false);

        barberService.deleteBarber(barberId);

        verify(displayOrderService).lockActiveOrderingScope(barberRepository);
        verify(barberRepository).delete(barber);
        verify(displayOrderService).shiftOrdersAfterDelete(0, barberRepository);
    }
}
