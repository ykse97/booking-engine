-- Aligns legacy employee schema with the current runtime model.
-- Existing deployments that still carry the historical is_barber column are
-- migrated in-place; deployments already aligned are left unchanged.
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
