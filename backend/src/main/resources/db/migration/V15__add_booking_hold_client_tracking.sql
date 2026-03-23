ALTER TABLE booking
    ADD COLUMN hold_client_ip VARCHAR(64),
    ADD COLUMN hold_client_device_id VARCHAR(128);

CREATE INDEX idx_booking_hold_client_ip_pending
    ON booking (hold_client_ip, status, expires_at);

CREATE INDEX idx_booking_hold_client_device_pending
    ON booking (hold_client_device_id, status, expires_at);
