-- A Proposition de photo: deposited by a Chef de groupe for their Groupe, waiting for the Bureau
ALTER TABLE gallery_photos ADD COLUMN proposition boolean DEFAULT false NOT NULL;
