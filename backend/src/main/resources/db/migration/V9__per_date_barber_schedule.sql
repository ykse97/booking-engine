-- Create per-date barber schedule table
CREATE TABLE barber_daily_schedule (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    barber_id UUID NOT NULL REFERENCES barber(id) ON DELETE CASCADE,
    working_date DATE NOT NULL,
    working_day BOOLEAN NOT NULL DEFAULT false,
    open_time TIME,
    close_time TIME,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_barber_date UNIQUE (barber_id, working_date)
);

-- Optional: seed next 30 days based on existing weekly hours if table barber_hour exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'barber_hour') THEN
        INSERT INTO barber_daily_schedule (barber_id, working_date, working_day, open_time, close_time)
        SELECT b.id,
               d::date AS working_date,
               COALESCE(h.working_day, false) AS working_day,
               CASE WHEN COALESCE(h.working_day, false) THEN h.open_time ELSE NULL END AS open_time,
               CASE WHEN COALESCE(h.working_day, false) THEN h.close_time ELSE NULL END AS close_time
        FROM barber b
        CROSS JOIN generate_series(current_date, current_date + interval '30 days', interval '1 day') AS d
        LEFT JOIN barber_hour h
            ON h.barber_id = b.id
           AND h.day_of_week = upper(to_char(d, 'DAY'))::varchar
        ON CONFLICT (barber_id, working_date) DO NOTHING;
    END IF;
END $$;

-- Drop legacy weekly table to avoid confusion
DROP TABLE IF EXISTS barber_hour;
