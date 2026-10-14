-- A sign-up's ride mode becomes its Poste, among five; a Groupe is de motards or de service (CONTEXT.md)
ALTER TABLE event_registrations RENAME COLUMN mode TO poste;
ALTER TABLE event_registrations DROP CONSTRAINT IF EXISTS event_registrations_mode_check;
ALTER TABLE event_registrations ADD CONSTRAINT event_registrations_poste_check
    CHECK (poste IN ('CONDUCTEUR', 'PASSAGER', 'CHAUFFEUR_CAMIONNETTE', 'POINT_FIXE', 'SERVICE'));
ALTER TABLE groups ADD COLUMN kind varchar(255) NOT NULL DEFAULT 'MOTARDS' CHECK (kind IN ('MOTARDS', 'SERVICE'));
