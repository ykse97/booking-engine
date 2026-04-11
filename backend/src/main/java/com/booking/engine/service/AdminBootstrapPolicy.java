package com.booking.engine.service;

import com.booking.engine.entity.AdminUserEntity;

/**
 * Service contract for admin bootstrap policy operations.
 * Defines admin bootstrap policy related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
public interface AdminBootstrapPolicy {

    /**
     * Executes normalize.
     *
     * @param value value value
     * @return result value
     */
    String normalize(String value);

    /**
     * Validates required credentials.
     *
     * @param username username value
     * @param password password value
     */
    void validateRequiredCredentials(String username, String password);

    /**
     * Validates bootstrap password.
     *
     * @param password password value
     */
    void validateBootstrapPassword(String password);

    /**
     * Validates may create first active admin.
     *
     * @param activeAdminExists active admin existence flag
     */
    void validateMayCreateFirstActiveAdmin(boolean activeAdminExists);

    /**
     * Ensures bootstrap account state.
     *
     * @param adminUser admin user entity
     * @return true when ensure bootstrap account state succeeds
     */
    boolean ensureBootstrapAccountState(AdminUserEntity adminUser);

    /**
     * Checks whether stored password.
     *
     * @param adminUser admin user entity
     * @return true when has stored password succeeds
     */
    boolean hasStoredPassword(AdminUserEntity adminUser);

    /**
     * Validates password overwrite allowed.
     *
     * @param created created flag
     * @param hasStoredPassword stored password flag
     * @param passwordMatches password match flag
     * @param allowPasswordOverwrite password overwrite flag
     */
    void validatePasswordOverwriteAllowed(
            boolean created,
            boolean hasStoredPassword,
            boolean passwordMatches,
            boolean allowPasswordOverwrite);

    /**
     * Checks whether password overwrite.
     *
     * @param created created flag
     * @param hasStoredPassword stored password flag
     * @return true when is password overwrite succeeds
     */
    boolean isPasswordOverwrite(boolean created, boolean hasStoredPassword);

    /**
     * Increments token version.
     *
     * @param adminUser admin user entity
     */
    void incrementTokenVersion(AdminUserEntity adminUser);

    /**
     * Resolves bootstrap reason code.
     *
     * @param created created flag
     * @param passwordOverwritten password overwrite flag
     * @return result value
     */
    String resolveBootstrapReasonCode(boolean created, boolean passwordOverwritten);
}
