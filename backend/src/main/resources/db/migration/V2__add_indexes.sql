CREATE INDEX idx_booking_barber_date
ON booking (barber_id, booking_date);

CREATE INDEX idx_booking_status
ON booking (status);

CREATE INDEX idx_booking_expires_at
ON booking (expires_at);