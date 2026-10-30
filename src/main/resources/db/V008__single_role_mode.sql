-- Mono-rôle : les nouvelles installations affichent le rôle système comme
-- « Gérant ». Les rôles historiques restent présents afin de préserver les
-- bases déjà utilisées ; ils seront traités par la migration de nettoyage
-- des données de démonstration.
UPDATE roles SET label = 'Gérant' WHERE code = 'ADMIN';

INSERT INTO settings (cle, valeur, groupe, type)
VALUES ('app.edition', 'SINGLE_ROLE', 'application', 'STRING')
ON DUPLICATE KEY UPDATE valeur = VALUES(valeur), groupe = VALUES(groupe), type = VALUES(type);
