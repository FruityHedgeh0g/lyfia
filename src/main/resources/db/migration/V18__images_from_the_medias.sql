-- An Event's image is one of the site's Medias, chosen with the image picker, instead of a URL
ALTER TABLE events ADD COLUMN media_id uuid REFERENCES medias (media_id);
ALTER TABLE events DROP COLUMN image_url;

-- One image may be the banner of several Posts
ALTER TABLE posts DROP CONSTRAINT IF EXISTS posts_media_id_key;
