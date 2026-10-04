-- A Post belongs to its author's Secteur, a carousel slide to its Secteur (ADR 0004); none: the whole site
ALTER TABLE posts ADD COLUMN sector_id uuid REFERENCES sectors (sector_id);
UPDATE posts SET sector_id = (SELECT users.sector_id FROM users WHERE users.user_id = posts.author_id)
WHERE author_id IS NOT NULL;

ALTER TABLE carousel_items ADD COLUMN sector_id uuid REFERENCES sectors (sector_id);
