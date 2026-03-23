package com.booking.engine.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.booking.engine.entity.HairSalonEntity;

/**
 * Repository for managing singleton {@link HairSalonEntity} persistence.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Repository
public interface HairSalonRepository extends JpaRepository<HairSalonEntity, UUID> {
}
