ALTER TABLE admin_user
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMP,
    ADD COLUMN last_failed_login_at TIMESTAMP;

CREATE INDEX idx_admin_user_locked_until_active
    ON admin_user(locked_until)
    WHERE active = TRUE AND locked_until IS NOT NULL;
