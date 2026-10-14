-- A pilote is now a conducteur (CONTEXT.md): the passager's link and the stored ride mode follow
ALTER TABLE event_registrations RENAME COLUMN pilote_registration_id TO conducteur_registration_id;
ALTER TABLE event_registrations DROP CONSTRAINT IF EXISTS event_registrations_mode_check;
UPDATE event_registrations SET mode = 'CONDUCTEUR' WHERE mode = 'PILOTE';
ALTER TABLE event_registrations ADD CONSTRAINT event_registrations_mode_check CHECK (mode IN ('CONDUCTEUR', 'PASSAGER'));
