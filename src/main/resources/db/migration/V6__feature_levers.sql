-- The Features are emergency levers (ADR 0009); the new ones ship on
INSERT INTO features (name, description, is_active) VALUES
    ('actualites', 'Montrer les actualités au public.', true),
    ('carrousel', 'Montrer le carrousel de la page d''accueil.', true),
    ('depot-medias', 'Permettre au Bureau de déposer des images dans la médiathèque.', true),
    ('demandes-fonctionnalite', 'Permettre au Bureau de demander des fonctionnalités.', true),
    ('export-liste', 'Permettre au Bureau de télécharger la liste des inscrits d''un événement.', true)
ON CONFLICT (name) DO NOTHING;

UPDATE features SET description = 'Permettre le don en ligne, depuis la page Faire un don (pas encore en place).' WHERE name = 'dons-en-ligne';
