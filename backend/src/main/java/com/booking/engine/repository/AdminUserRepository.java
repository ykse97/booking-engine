package com.booking.engine.repository;

import com.booking.engine.entity.AdminUserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for admin user authentication data.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Repository
public interface AdminUserRepository extends JpaRepository<AdminUserEntity, UUID> {

    /**
     * Finds active admin user by username.
     *
     * @param username username
     * @return optional admin user
     */
    Optional<AdminUserEntity> findByUsernameAndActiveTrue(String username);

    /**
     * Finds admin user by username regardless of active flag.
     *
     * @param username username
     * @return optional admin user
     */
    Optional<AdminUserEntity> findByUsername(String username);

    /**
     * Checks whether username already exists.
     *
     * @param username username
     * @return true when user exists
     */
    boolean existsByUsername(String username);
}
