CREATE TABLE booking_blacklist_entry (
    id UUID PRIMARY KEY,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    email VARCHAR(255),
    email_normalized VARCHAR(255),
    phone VARCHAR(50),
    phone_normalized VARCHAR(50),
    reason VARCHAR(500),
    CONSTRAINT chk_booking_blacklist_entry_contact
        CHECK (email_normalized IS NOT NULL OR phone_normalized IS NOT NULL)
);

CREATE UNIQUE INDEX uq_booking_blacklist_entry_email_active
    ON booking_blacklist_entry (email_normalized)
    WHERE active = TRUE AND email_normalized IS NOT NULL;

CREATE UNIQUE INDEX uq_booking_blacklist_entry_phone_active
    ON booking_blacklist_entry (phone_normalized)
    WHERE active = TRUE AND phone_normalized IS NOT NULL;
