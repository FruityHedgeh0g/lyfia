-- A person's e-mail (their Keycloak account's) and birth date; the décharge a minor's legal representative approves
ALTER TABLE users ADD COLUMN email varchar(255);
ALTER TABLE users ADD COLUMN birth_date date;

-- Each uploaded version of the décharge, kept so that each signature points at the very text approved
CREATE TABLE decharge_documents (
    document_id uuid NOT NULL PRIMARY KEY,
    original_filename varchar(255) NOT NULL,
    file_size bigint NOT NULL,
    uploaded_at timestamp(6) NOT NULL,
    uploaded_by uuid NOT NULL,
    content bytea NOT NULL
);

-- Who approved the décharge for a minor's sign-up, when, and which version
ALTER TABLE event_registrations ADD COLUMN decharge_signataire varchar(255);
ALTER TABLE event_registrations ADD COLUMN decharge_signed_at timestamp(6);
ALTER TABLE event_registrations ADD COLUMN decharge_document_id uuid REFERENCES decharge_documents (document_id);
