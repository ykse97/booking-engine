-- Restores the historical employee bookability migration so existing Flyway
-- histories can still validate successfully after the schema was rebased onto
-- baseline snapshots.
--
-- V1 creates both `is_barber` and `bookable`; this migration moves the runtime
-- flag to `bookable`, drops the legacy column when it still exists, and keeps
-- the newer default aligned with the current model.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'employee'
          AND column_name = 'is_barber'
    ) THEN
        UPDATE employee
        SET bookable = is_barber;

        ALTER TABLE employee
            DROP COLUMN is_barber;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'employee'
          AND column_name = 'bookable'
    ) THEN
        ALTER TABLE employee
            ALTER COLUMN bookable SET DEFAULT FALSE;
    END IF;
END $$;
