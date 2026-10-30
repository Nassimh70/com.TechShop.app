-- Données de référence pour une installation neuve en profil prod.
-- Aucun compte, produit, fournisseur, client, vente ou réparation n'est créé.

INSERT IGNORE INTO roles (code, label, description)
VALUES ('ADMIN', 'Gérant', 'Accès complet à tous les modules');

INSERT IGNORE INTO permissions (role_id, module, can_view, can_create, can_edit, can_delete, can_validate)
SELECT r.id, m.module, 1, 1, 1, 1, 1
FROM roles r
CROSS JOIN (
    SELECT 'dashboard' AS module UNION ALL SELECT 'pos' UNION ALL SELECT 'sales'
    UNION ALL SELECT 'customers' UNION ALL SELECT 'products' UNION ALL SELECT 'stock'
    UNION ALL SELECT 'repairs' UNION ALL SELECT 'cash' UNION ALL SELECT 'reports'
    UNION ALL SELECT 'users' UNION ALL SELECT 'settings'
) m
WHERE r.code = 'ADMIN';

INSERT IGNORE INTO categories (name, parent_id) VALUES
    ('Téléphones', NULL),
    ('Accessoires', NULL),
    ('Pièces détachées', NULL),
    ('Outils informatiques', NULL);

INSERT IGNORE INTO expense_categories (name, description) VALUES
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
    ('numbering.cash.prefix', 'C', 'caisse', 'STRING')
ON DUPLICATE KEY UPDATE valeur = VALUES(valeur), groupe = VALUES(groupe), type = VALUES(type);
