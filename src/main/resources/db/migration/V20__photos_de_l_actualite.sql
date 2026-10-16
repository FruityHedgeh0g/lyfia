-- A Post's "Photos de l'actualité" keep the order the Bureau chose
ALTER TABLE post_attachments ADD COLUMN position integer DEFAULT 0 NOT NULL;
