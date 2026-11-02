ALTER TABLE groups
    ADD COLUMN name TEXT,
    ADD CONSTRAINT groups_name_valid CHECK (
        name = btrim(name) AND char_length(name) BETWEEN 1 AND 100
    );
