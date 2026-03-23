CREATE TABLE barber_hour (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    barber_id UUID NOT NULL REFERENCES barber(id) ON DELETE CASCADE,
    day_of_week VARCHAR(20) NOT NULL,
    working_day BOOLEAN NOT NULL DEFAULT false,
    open_time TIME,
    close_time TIME,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT unique_barber_day UNIQUE (barber_id, day_of_week)
);

-- Backfill existing barbers using salon hours template.
INSERT INTO barber_hour (barber_id, day_of_week, working_day, open_time, close_time)
SELECT b.id, h.day_of_week, h.working_day, h.open_time, h.close_time
FROM barber b
CROSS JOIN hair_salon_hour h
ON CONFLICT (barber_id, day_of_week) DO NOTHING;
