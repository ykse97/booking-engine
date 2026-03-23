CREATE TABLE barber_schedule_period_settings (
    id INTEGER PRIMARY KEY,
    target_barber_id UUID REFERENCES barber(id),
    apply_to_all_barbers BOOLEAN NOT NULL DEFAULT TRUE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL
);

INSERT INTO barber_schedule_period_settings (id, target_barber_id, apply_to_all_barbers, start_date, end_date)
VALUES (1, NULL, TRUE, CURRENT_DATE, CURRENT_DATE);

CREATE TABLE barber_schedule_period_day_settings (
    day_of_week VARCHAR(16) PRIMARY KEY,
    working_day BOOLEAN NOT NULL DEFAULT FALSE,
    open_time TIME,
    close_time TIME,
    break_start_time TIME,
    break_end_time TIME
);

INSERT INTO barber_schedule_period_day_settings (day_of_week, working_day, open_time, close_time, break_start_time, break_end_time)
VALUES
    ('MONDAY', FALSE, NULL, NULL, NULL, NULL),
    ('TUESDAY', FALSE, NULL, NULL, NULL, NULL),
    ('WEDNESDAY', FALSE, NULL, NULL, NULL, NULL),
    ('THURSDAY', FALSE, NULL, NULL, NULL, NULL),
    ('FRIDAY', FALSE, NULL, NULL, NULL, NULL),
    ('SATURDAY', FALSE, NULL, NULL, NULL, NULL),
    ('SUNDAY', FALSE, NULL, NULL, NULL, NULL);
