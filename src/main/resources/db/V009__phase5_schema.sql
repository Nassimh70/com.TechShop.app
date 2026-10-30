-- Évolution structurelle conservée séparément des données de démonstration.
-- V006 contient aussi cette colonne, mais il est exclu du profil prod.
ALTER TABLE repair_tickets
    ADD COLUMN IF NOT EXISTS quoted_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00
    COMMENT 'Devis estime' AFTER diagnosis;
