-- Sitting on a Bureau is not only a Role (ADR 0001): the Président flag becomes a Fonction au Bureau,
-- held by everyone with the Bureau Role and, by choice, by an Admin
ALTER TABLE users ADD COLUMN bureau_function varchar(255)
    CHECK (bureau_function IN ('MEMBRE_DU_BUREAU', 'PRESIDENT'));
UPDATE users SET bureau_function = 'PRESIDENT' WHERE president;
UPDATE users SET bureau_function = 'MEMBRE_DU_BUREAU' WHERE role = 'BUREAU' AND bureau_function IS NULL;
ALTER TABLE users DROP COLUMN president;
