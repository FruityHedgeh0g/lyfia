-- A Feature's lever for one Secteur, independent of the site-wide one (ADR 0009); none means on
CREATE TABLE feature_sector_levers (
    lever_id uuid NOT NULL PRIMARY KEY,
    feature varchar(255) NOT NULL,
    sector_id uuid NOT NULL REFERENCES sectors (sector_id),
    is_active boolean NOT NULL,
    CONSTRAINT feature_sector_levers_feature_sector UNIQUE (feature, sector_id)
);
