-- A Bénévole sans compte, recorded by the Bureau, has no Keycloak account (ADR 0012)
ALTER TABLE users ADD COLUMN without_account boolean NOT NULL DEFAULT false;

-- The Bureau member who recorded a décharge signed on paper
ALTER TABLE event_registrations ADD COLUMN decharge_recorded_by uuid;
