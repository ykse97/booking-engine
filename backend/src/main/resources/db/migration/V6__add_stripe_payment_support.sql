ALTER TABLE booking
ADD COLUMN stripe_payment_intent_id VARCHAR(255),
ADD COLUMN stripe_payment_status VARCHAR(100),
ADD COLUMN hold_amount NUMERIC(10,2),
ADD COLUMN payment_captured_at TIMESTAMP,
ADD COLUMN payment_released_at TIMESTAMP;

CREATE UNIQUE INDEX uq_booking_stripe_payment_intent_id
ON booking(stripe_payment_intent_id)
WHERE stripe_payment_intent_id IS NOT NULL;

CREATE TABLE booking_payment_settings (
    id INT PRIMARY KEY,
    hold_amount NUMERIC(10,2) NOT NULL
);

INSERT INTO booking_payment_settings (id, hold_amount)
VALUES (1, 10.00)
ON CONFLICT (id) DO NOTHING;
