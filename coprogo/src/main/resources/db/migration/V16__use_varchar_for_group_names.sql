ALTER TABLE groups
    ALTER COLUMN name TYPE VARCHAR(100),
    DROP CONSTRAINT groups_name_valid,
    ADD CONSTRAINT groups_name_valid CHECK (
        name = btrim(name) AND char_length(name) > 0
    );
