-- Index for ordering queries

CREATE INDEX idx_barber_order
ON barber(display_order);

CREATE INDEX idx_treatment_order
ON treatment(display_order);

-- Unique constraint only for active barbers
-- Allows inactive barbers to keep their order

CREATE UNIQUE INDEX uq_barber_display_order_active
ON barber(display_order)
WHERE active = true;

CREATE UNIQUE INDEX uq_treatment_display_order_active
ON treatment(display_order)
WHERE active = true;