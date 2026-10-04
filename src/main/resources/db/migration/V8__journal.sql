-- The Journal: every time a Feature was turned off or on (ADR 0009); never changed nor deleted from the site
CREATE TABLE feature_switches (
    switch_id uuid NOT NULL PRIMARY KEY,
    feature varchar(255) NOT NULL,
    sector_id uuid REFERENCES sectors (sector_id),
    is_active boolean NOT NULL,
    reason text,
    switched_by uuid REFERENCES users (user_id),
    switched_at timestamp(6) NOT NULL
);
CREATE INDEX feature_switches_switched_at ON feature_switches (switched_at DESC);

-- How many attempts each Feature refused since it was last turned off
ALTER TABLE features ADD COLUMN refused_count bigint NOT NULL DEFAULT 0;
