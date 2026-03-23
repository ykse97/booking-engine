CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ==================== HAIR SALON ====================

CREATE TABLE hair_salon (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    email VARCHAR(255),
    phone VARCHAR(50),
    address VARCHAR(500) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- ==================== HAIR SALON HOURS ====================

CREATE TABLE hair_salon_hour (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    hair_salon_id UUID NOT NULL REFERENCES hair_salon(id) ON DELETE CASCADE,
    day_of_week VARCHAR(20) NOT NULL,
    working_day BOOLEAN NOT NULL DEFAULT false,
    open_time TIME,
    close_time TIME,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT unique_hair_salon_day UNIQUE (hair_salon_id, day_of_week)
);

-- ==================== BARBER ====================

CREATE TABLE barber (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    bio TEXT,
    photo_url VARCHAR(500),
    display_order INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- ==================== TREATMENT ====================

CREATE TABLE treatment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    duration_minutes INT NOT NULL,
    price NUMERIC(10,2) NOT NULL,
    photo_url VARCHAR(500),
    display_order INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- ==================== BOOKING ====================

CREATE TABLE booking (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    barber_id UUID REFERENCES barber(id),
    treatment_id UUID REFERENCES treatment(id),
    customer_name VARCHAR(255),
    customer_email VARCHAR(255),
    customer_phone VARCHAR(50),
    booking_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status VARCHAR(50) NOT NULL,
    expires_at TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);