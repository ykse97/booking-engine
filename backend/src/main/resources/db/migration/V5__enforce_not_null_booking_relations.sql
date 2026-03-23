-- Enforce referential integrity for bookings.
-- Existing invalid rows are removed before NOT NULL constraints are applied.

DELETE FROM booking
WHERE barber_id IS NULL
   OR treatment_id IS NULL;

ALTER TABLE booking
ALTER COLUMN barber_id SET NOT NULL;

ALTER TABLE booking
ALTER COLUMN treatment_id SET NOT NULL;
