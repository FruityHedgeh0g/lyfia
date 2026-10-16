-- The files of the Medias behind a swappable storage, by file key (ADR 0014); a Media's original is "<file key>/original"
CREATE TABLE media_files (
    file_key varchar(255) NOT NULL PRIMARY KEY,
    mime_type varchar(255) NOT NULL,
    content bytea NOT NULL
);
UPDATE medias SET file_key = CAST(media_id AS varchar(36));
INSERT INTO media_files (file_key, mime_type, content)
    SELECT CAST(c.media_id AS varchar(36)) || '/original', COALESCE(m.mime_type, 'image/jpeg'), c.content
    FROM media_contents c JOIN medias m ON m.media_id = c.media_id;
DROP TABLE media_contents;

-- The Photos de la Galerie: of one Secteur and one Année, optionally of an Event and of Groupes (ADR 0013)
CREATE TABLE gallery_photos (
    photo_id uuid NOT NULL PRIMARY KEY,
    media_id uuid NOT NULL UNIQUE REFERENCES medias (media_id),
    sector_id uuid NOT NULL REFERENCES sectors (sector_id),
    annee integer NOT NULL,
    event_id uuid REFERENCES events (event_id),
    visibilite varchar(255) DEFAULT 'BROUILLON' NOT NULL CHECK (visibilite IN ('BROUILLON', 'INSCRITS', 'PUBLIQUE')),
    deposited_by uuid,
    deposited_at timestamp(6) NOT NULL,
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    updated_by uuid
);
CREATE TABLE gallery_photo_groups (
    photo_id uuid NOT NULL REFERENCES gallery_photos (photo_id),
    group_id uuid NOT NULL REFERENCES groups (group_id),
    PRIMARY KEY (photo_id, group_id)
);

-- The largest photo kept, which the depositor's browser scales down to (ADR 0014)
INSERT INTO configurations (name, config_value, created_at, updated_at) VALUES
    ('galerie.photo.max-px', '3000', now(), now()),
    ('galerie.photo.max-mo', '5', now(), now())
ON CONFLICT (name) DO NOTHING;
