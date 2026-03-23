package com.booking.engine.repository;

import com.booking.engine.entity.BookingBlacklistEntryEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for booking blacklist entries.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Repository
public interface BookingBlacklistEntryRepository extends JpaRepository<BookingBlacklistEntryEntity, UUID> {

    /**
     * Finds active blacklist entries ordered by creation timestamp descending.
     *
     * @return active blacklist entries
     */
    List<BookingBlacklistEntryEntity> findByActiveTrueOrderByCreatedAtDesc();

    /**
     * Finds active blacklist entry by identifier.
     *
     * @param id blacklist entry identifier
     * @return optional active blacklist entry
     */
    Optional<BookingBlacklistEntryEntity> findByIdAndActiveTrue(UUID id);

    /**
     * Checks whether normalized email is blacklisted.
     *
     * @param emailNormalized normalized email value
     * @return true when matching active blacklist entry exists
     */
    boolean existsByActiveTrueAndEmailNormalized(String emailNormalized);

    /**
     * Checks whether normalized phone number is blacklisted.
     *
     * @param phoneNormalized normalized phone value
     * @return true when matching active blacklist entry exists
     */
    boolean existsByActiveTrueAndPhoneNormalized(String phoneNormalized);
}
