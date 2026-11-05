-- ============================================================================
-- TechShop Manager - SQLite reference data (V002)
-- Données de référence communes aux profils prod et demo :
-- catégories, catégories de dépenses et paramètres par défaut.
-- Aucun compte utilisateur n'est créé ici : le profil prod passe par l'écran
-- de configuration initiale, le profil demo ajoute ses comptes dans V003.
-- ============================================================================

INSERT OR IGNORE INTO categories (name, parent_id) VALUES
    ('Téléphones', NULL),
    ('Accessoires', NULL),
    ('Pièces détachées', NULL),
    ('Outils informatiques', NULL);

INSERT OR IGNORE INTO expense_categories (name, description) VALUES
    ('Loyer', 'Loyer du local'),
    ('Salaires', 'Rémunération du personnel'),
    ('Électricité / Internet', 'Charges'),
    ('Transport', 'Livraisons et déplacements'),
    ('Fournitures', 'Consommables et petit matériel'),
    ('Autres', 'Dépenses diverses');

INSERT INTO settings (cle, valeur, groupe, type) VALUES
    ('shop.name', '', 'magasin', 'STRING'),
    ('shop.address', '', 'magasin', 'STRING'),
    ('shop.phone', '', 'magasin', 'STRING'),
    ('shop.logo', '', 'magasin', 'STRING'),
    ('ticket.footer', 'Merci de votre confiance.', 'impression', 'STRING'),
    ('sale.vat_default', '19', 'ventes', 'DECIMAL'),
    ('sale.allow_negative_stock', '0', 'ventes', 'BOOLEAN'),
    ('stock.alert_threshold', '5', 'stock', 'INT'),
    ('apparence.theme', 'LIGHT', 'apparence', 'STRING'),
    ('apparence.font_size', '13', 'apparence', 'INT'),
    ('numbering.sale.prefix', 'V', 'ventes', 'STRING'),
    ('numbering.repair.prefix', 'R', 'service', 'STRING'),
    ('numbering.cash.prefix', 'C', 'caisse', 'STRING'),
    ('app.edition', 'SINGLE_ROLE', 'application', 'STRING')
ON CONFLICT(cle) DO UPDATE SET
    valeur = excluded.valeur,
    groupe = excluded.groupe,
    type = excluded.type;
