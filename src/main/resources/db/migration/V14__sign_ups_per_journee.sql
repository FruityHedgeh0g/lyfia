-- Sign-ups per Journée (ADR 0011): each Journée signed up for has its place, its order on the Liste d'attente and,
-- for a passager, the conducteur they ride behind that Journée; Poste and Groupe stay on the Event's sign-up
CREATE TABLE journee_registrations (
    journee_registration_id uuid NOT NULL PRIMARY KEY,
    registration_id uuid NOT NULL REFERENCES event_registrations ON DELETE CASCADE,
    journee date NOT NULL,
    waiting boolean NOT NULL,
    signed_up_at timestamp(6) NOT NULL,
    conducteur_journee_id uuid UNIQUE REFERENCES journee_registrations ON DELETE SET NULL,
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    updated_by uuid,
    UNIQUE (registration_id, journee)
);

-- Every existing sign-up covers each Journée of its Event, as it stood
INSERT INTO journee_registrations (journee_registration_id, registration_id, journee, waiting, signed_up_at, created_at, updated_at)
SELECT gen_random_uuid(), r.registration_id, d::date, r.waiting, r.signed_up_at, r.created_at, r.updated_at
FROM event_registrations r
JOIN events e ON e.event_id = r.event_id
CROSS JOIN LATERAL generate_series(e.startDateTime::date, COALESCE(e.endDateTime, e.startDateTime)::date, interval '1 day') AS d;

-- A passager rides behind their conducteur each Journée both are at
UPDATE journee_registrations p
SET conducteur_journee_id = c.journee_registration_id
FROM event_registrations pr, journee_registrations c
WHERE pr.registration_id = p.registration_id
  AND pr.conducteur_registration_id = c.registration_id
  AND c.journee = p.journee;

ALTER TABLE event_registrations DROP COLUMN conducteur_registration_id;
ALTER TABLE event_registrations DROP COLUMN waiting;
