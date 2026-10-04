-- Inscription sur le site also opens and closes Keycloak's registration form (ADR 0009)
INSERT INTO features (name, description, is_active) VALUES
    ('inscription-site', 'Permettre aux visiteurs de créer leur compte sur le site.', true)
ON CONFLICT (name) DO NOTHING;
