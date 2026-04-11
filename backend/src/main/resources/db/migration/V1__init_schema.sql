CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ==================== CORE TABLES ====================

CREATE TABLE hair_salon (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    email VARCHAR(255),
    phone VARCHAR(50),
    address VARCHAR(500) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE hair_salon_hour (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    hair_salon_id UUID NOT NULL REFERENCES hair_salon(id) ON DELETE CASCADE,
    day_of_week VARCHAR(20) NOT NULL,
    working_day BOOLEAN NOT NULL DEFAULT FALSE,
    open_time TIME,
    close_time TIME,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT unique_hair_salon_day UNIQUE (hair_salon_id, day_of_week)
);

CREATE TABLE employee (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    role VARCHAR(255),
    bio TEXT,
    photo_url VARCHAR(500),
    display_order INTEGER NOT NULL,
    is_barber BOOLEAN NOT NULL DEFAULT FALSE,
    bookable BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE treatment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    duration_minutes INT NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    photo_url VARCHAR(500),
    description TEXT NOT NULL DEFAULT '',
    display_order INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE booking (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    employee_id UUID NOT NULL REFERENCES employee(id),
    treatment_id UUID NOT NULL REFERENCES treatment(id),
    customer_name VARCHAR(255),
    customer_email VARCHAR(255),
    customer_phone VARCHAR(50),
    booking_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status VARCHAR(50) NOT NULL,
    expires_at TIMESTAMP,
    stripe_payment_intent_id VARCHAR(255),
    stripe_payment_status VARCHAR(100),
    hold_amount NUMERIC(10, 2),
    hold_client_ip VARCHAR(64),
    hold_client_device_id VARCHAR(128),
    payment_captured_at TIMESTAMP,
    payment_released_at TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE employee_daily_schedule (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    employee_id UUID NOT NULL REFERENCES employee(id) ON DELETE CASCADE,
    working_date DATE NOT NULL,
    working_day BOOLEAN NOT NULL DEFAULT FALSE,
    open_time TIME,
    close_time TIME,
    break_start_time TIME,
    break_end_time TIME,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_employee_date UNIQUE (employee_id, working_date)
);

CREATE TABLE employee_schedule_period_settings (
    id INTEGER PRIMARY KEY,
    target_employee_id UUID REFERENCES employee(id),
    apply_to_all_employees BOOLEAN NOT NULL DEFAULT TRUE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL
);

CREATE TABLE employee_schedule_period_day_settings (
    day_of_week VARCHAR(16) PRIMARY KEY,
    working_day BOOLEAN NOT NULL DEFAULT FALSE,
    open_time TIME,
    close_time TIME,
    break_start_time TIME,
    break_end_time TIME
);

CREATE TABLE booking_blacklist_entry (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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

CREATE TABLE admin_user (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- ==================== INDEXES ====================

CREATE INDEX idx_booking_employee_date ON booking(employee_id, booking_date);
CREATE INDEX idx_booking_status ON booking(status);
CREATE INDEX idx_booking_expires_at ON booking(expires_at);
CREATE INDEX idx_booking_hold_client_ip_pending ON booking(hold_client_ip, status, expires_at);
CREATE INDEX idx_booking_hold_client_device_pending ON booking(hold_client_device_id, status, expires_at);
CREATE UNIQUE INDEX uq_booking_stripe_payment_intent_id
    ON booking(stripe_payment_intent_id)
    WHERE stripe_payment_intent_id IS NOT NULL;

CREATE INDEX idx_employee_order ON employee(display_order);
CREATE INDEX idx_treatment_order ON treatment(display_order);

CREATE UNIQUE INDEX uq_employee_display_order_active
    ON employee(display_order)
    WHERE active = TRUE;

CREATE UNIQUE INDEX uq_treatment_display_order_active
    ON treatment(display_order)
    WHERE active = TRUE;

CREATE UNIQUE INDEX uq_booking_blacklist_entry_email_active
    ON booking_blacklist_entry(email_normalized)
    WHERE active = TRUE AND email_normalized IS NOT NULL;

CREATE UNIQUE INDEX uq_booking_blacklist_entry_phone_active
    ON booking_blacklist_entry(phone_normalized)
    WHERE active = TRUE AND phone_normalized IS NOT NULL;

-- ==================== INITIAL DATA ====================

INSERT INTO hair_salon (id, name, description, email, phone, address)
VALUES (
    '550e8400-e29b-41d4-a716-446655440000',
    'Barber Shop',
    'A trendy barber shop offering classic and modern haircuts.',
    'info@barbershop.com',
    '+353874749999',
    '123 Main St, Dublin, Ireland'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO hair_salon_hour (hair_salon_id, day_of_week, working_day, open_time, close_time)
VALUES
    ('550e8400-e29b-41d4-a716-446655440000', 'MONDAY', TRUE, '09:00', '18:00'),
    ('550e8400-e29b-41d4-a716-446655440000', 'TUESDAY', TRUE, '09:00', '18:00'),
    ('550e8400-e29b-41d4-a716-446655440000', 'WEDNESDAY', TRUE, '09:00', '18:00'),
    ('550e8400-e29b-41d4-a716-446655440000', 'THURSDAY', TRUE, '09:00', '18:00'),
    ('550e8400-e29b-41d4-a716-446655440000', 'FRIDAY', TRUE, '09:00', '18:00'),
    ('550e8400-e29b-41d4-a716-446655440000', 'SATURDAY', TRUE, '10:00', '16:00'),
    ('550e8400-e29b-41d4-a716-446655440000', 'SUNDAY', FALSE, NULL, NULL)
ON CONFLICT (hair_salon_id, day_of_week) DO NOTHING;

INSERT INTO employee_schedule_period_settings (id, target_employee_id, apply_to_all_employees, start_date, end_date)
VALUES (1, NULL, TRUE, CURRENT_DATE, CURRENT_DATE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO employee_schedule_period_day_settings (day_of_week, working_day, open_time, close_time, break_start_time, break_end_time)
VALUES
    ('MONDAY', FALSE, NULL, NULL, NULL, NULL),
    ('TUESDAY', FALSE, NULL, NULL, NULL, NULL),
    ('WEDNESDAY', FALSE, NULL, NULL, NULL, NULL),
    ('THURSDAY', FALSE, NULL, NULL, NULL, NULL),
    ('FRIDAY', FALSE, NULL, NULL, NULL, NULL),
    ('SATURDAY', FALSE, NULL, NULL, NULL, NULL),
    ('SUNDAY', FALSE, NULL, NULL, NULL, NULL)
ON CONFLICT (day_of_week) DO NOTHING;

INSERT INTO admin_user (username, password_hash, role, active)
VALUES ('admin', '$2a$10$/b8Kp9eds4FUCFT6ETS9jO2HiucVz4k3MUBdkP1BNCmX9iHZKJUmW', 'ADMIN', TRUE)
ON CONFLICT (username) DO NOTHING;