package com.booking.engine.service.impl;

import com.booking.engine.dto.BookingBlacklistEntryRequestDto;
import com.booking.engine.dto.BookingBlacklistEntryResponseDto;
import com.booking.engine.entity.BookingBlacklistEntryEntity;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.repository.BookingBlacklistEntryRepository;
import com.booking.engine.service.BookingBlacklistService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link BookingBlacklistService}.
 * Handles blacklist validation and entry lifecycle for customer contact data.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingBlacklistServiceImpl implements BookingBlacklistService {

    // ---------------------- Repositories ----------------------

    private final BookingBlacklistEntryRepository blacklistRepository;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateAllowedCustomer(String email, String phone) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedPhone = normalizePhone(phone);

        if (normalizedEmail != null && blacklistRepository.existsByActiveTrueAndEmailNormalized(normalizedEmail)) {
            throw new BookingValidationException(
                    "This email address is in the booking blacklist and cannot be used for appointments.");
        }

        if (normalizedPhone != null && blacklistRepository.existsByActiveTrueAndPhoneNormalized(normalizedPhone)) {
            throw new BookingValidationException(
                    "This phone number is in the booking blacklist and cannot be used for appointments.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBlockedCustomer(String email, String phone) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedPhone = normalizePhone(phone);

        return (normalizedEmail != null && blacklistRepository.existsByActiveTrueAndEmailNormalized(normalizedEmail))
                || (normalizedPhone != null && blacklistRepository.existsByActiveTrueAndPhoneNormalized(normalizedPhone));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BookingBlacklistEntryResponseDto> getActiveEntries() {
        return blacklistRepository.findByActiveTrueOrderByCreatedAtDesc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public BookingBlacklistEntryResponseDto createEntry(BookingBlacklistEntryRequestDto request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        String normalizedPhone = normalizePhone(request.getPhone());

        if (normalizedEmail == null && normalizedPhone == null) {
            throw new BookingValidationException("Enter at least a phone number or an email for the blacklist.");
        }

        if (normalizedEmail != null && blacklistRepository.existsByActiveTrueAndEmailNormalized(normalizedEmail)) {
            throw new BookingValidationException("This email address is already in the booking blacklist.");
        }

        if (normalizedPhone != null && blacklistRepository.existsByActiveTrueAndPhoneNormalized(normalizedPhone)) {
            throw new BookingValidationException("This phone number is already in the booking blacklist.");
        }

        BookingBlacklistEntryEntity entry = BookingBlacklistEntryEntity.builder()
                .active(true)
                .email(cleanValue(request.getEmail()))
                .emailNormalized(normalizedEmail)
                .phone(cleanValue(request.getPhone()))
                .phoneNormalized(normalizedPhone)
                .reason(cleanValue(request.getReason()))
                .build();

        BookingBlacklistEntryEntity savedEntry = blacklistRepository.save(entry);
        log.info("Booking blacklist entry created id={}", savedEntry.getId());
        return toDto(savedEntry);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteEntry(UUID id) {
        BookingBlacklistEntryEntity entry = blacklistRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new EntityNotFoundException("BookingBlacklistEntry", id));

        entry.setActive(false);
        blacklistRepository.save(entry);
        log.info("Booking blacklist entry deactivated id={}", id);
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Maps a persisted blacklist entity into the response DTO returned by admin
     * endpoints.
     *
     * @param entity blacklist entity
     * @return response DTO
     */
    private BookingBlacklistEntryResponseDto toDto(BookingBlacklistEntryEntity entity) {
        return BookingBlacklistEntryResponseDto.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .reason(entity.getReason())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /*
     * Trims a raw contact value and converts blank input to {@code null} so both
     * normalization and persistence operate on consistent optional values.
     *
     * @param value raw field value
     * @return cleaned value or {@code null} when blank
     */
    private String cleanValue(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    /*
     * Normalizes an email address by trimming it and lowercasing the result for
     * case-insensitive blacklist comparisons.
     *
     * @param email raw email value
     * @return normalized email or {@code null} when blank
     */
    private String normalizeEmail(String email) {
        String cleaned = cleanValue(email);
        return cleaned == null ? null : cleaned.toLowerCase();
    }

    /*
     * Normalizes a phone number by trimming it and keeping digits only so format
     * differences do not affect blacklist matching.
     *
     * @param phone raw phone value
     * @return normalized digit-only phone or {@code null} when blank
     */
    private String normalizePhone(String phone) {
        String cleaned = cleanValue(phone);
        if (cleaned == null) {
            return null;
        }

        String normalized = cleaned.replaceAll("[^0-9]", "");
        return normalized.isBlank() ? null : normalized;
    }
}
