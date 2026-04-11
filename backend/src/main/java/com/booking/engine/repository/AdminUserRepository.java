package com.booking.engine.repository;

import com.booking.engine.entity.AdminUserEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
     * Finds active admin user by username using a write lock.
     *
     * @param username username
     * @return optional locked admin user
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AdminUserEntity u where u.username = :username and u.active = true")
    Optional<AdminUserEntity> findByUsernameAndActiveTrueForUpdate(@Param("username") String username);

    /**
     * Finds admin user by username regardless of active flag.
     *
     * @param username username
     * @return optional admin user
     */
    Optional<AdminUserEntity> findByUsername(String username);

    /**
     * Checks whether any active admin account currently exists.
     *
     * @return true when at least one active admin user exists
     */
    boolean existsByActiveTrue();

    /**
     * Checks whether username already exists.
     *
     * @param username username
     * @return true when user exists
     */
    boolean existsByUsername(String username);
}
