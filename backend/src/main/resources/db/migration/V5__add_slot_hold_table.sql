CREATE TABLE slot_hold (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    employee_id UUID NOT NULL REFERENCES employee(id) ON DELETE CASCADE,
    treatment_id UUID NOT NULL REFERENCES treatment(id) ON DELETE CASCADE,
    booking_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    hold_scope VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    hold_amount NUMERIC(10, 2),
    hold_client_ip VARCHAR(64),
    hold_client_device_id VARCHAR(128),
    customer_name VARCHAR(255),
    customer_email VARCHAR(255),
    customer_phone VARCHAR(50),
    stripe_payment_intent_id VARCHAR(255),
    stripe_payment_status VARCHAR(100),
    payment_captured_at TIMESTAMP,
    payment_released_at TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_slot_hold_employee_date ON slot_hold(employee_id, booking_date);
CREATE INDEX idx_slot_hold_expires_at ON slot_hold(expires_at);
CREATE INDEX idx_slot_hold_scope_device ON slot_hold(hold_scope, hold_client_device_id, expires_at);
CREATE INDEX idx_slot_hold_scope_ip ON slot_hold(hold_scope, hold_client_ip, expires_at);
CREATE UNIQUE INDEX uq_slot_hold_stripe_payment_intent_id
    ON slot_hold(stripe_payment_intent_id)
    WHERE stripe_payment_intent_id IS NOT NULL;
