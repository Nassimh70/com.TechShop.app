-- ============================================================================
-- TechShop Manager - V002__initial_data.sql
-- Données initiales de la base (SPEC §3, §5) :
--   * rôles ADMIN / VENDEUR / TECHNICIEN
--   * matrice de permissions par défaut (module x action)
--   * utilisateur administrateur initial (doit changer son mot de passe)
--   * catégories de produits, catégories de dépenses
--   * paramètres par défaut du magasin, des ventes, de l'impression, du stock
-- Le mot de passe de l'admin initial est haché en BCrypt (jBCrypt, coût 10) :
--   identifiant : admin   /   mot de passe : admin123  (à changer 1re connexion)
-- ============================================================================

-- ------------------------------------------------------------------ Rôles ---
INSERT INTO roles (code, label, description) VALUES
    ('ADMIN', 'Administrateur', 'Acces complet a tous les modules'),
    ('VENDEUR', 'Vendeur', 'Point de vente, ventes, clients, consultation stock'),
    ('TECHNICIEN', 'Technicien', 'SAV/reparations, pieces, consultation stock');

-- ---------------------------------------------- Utilisateur admin initial ---
-- Mot de passe haché en BCrypt : admin123 (changement obligatoire a la 1re connexion)
INSERT INTO users (username, password_hash, full_name, role_id, active, must_change_password)
SELECT 'admin',
       '$2a$10$VH5Os9q.7BXdmrdGynqpv.XLhWQvGClPvr7mrgKvv3AenFXdi3M8i',
       'Administrateur',
       r.id,
       1,
       1
FROM roles r WHERE r.code = 'ADMIN';

-- ------------------------------------------------ Permissions par defaut ---
-- ADMIN : tous les droits sur tous les modules.
INSERT INTO permissions (role_id, module, can_view, can_create, can_edit, can_delete, can_validate)
SELECT r.id, m.module, 1, 1, 1, 1, 1
FROM roles r
CROSS JOIN (
    SELECT 'dashboard' AS module UNION ALL SELECT 'pos' UNION ALL SELECT 'sales'
    UNION ALL SELECT 'customers' UNION ALL SELECT 'products' UNION ALL SELECT 'stock'
    UNION ALL SELECT 'repairs' UNION ALL SELECT 'cash'
    UNION ALL SELECT 'reports' UNION ALL SELECT 'users' UNION ALL SELECT 'settings'
) m
WHERE r.code = 'ADMIN';

-- VENDEUR : point de vente, ventes, clients, consultation produits/stock, caisse.
INSERT INTO permissions (role_id, module, can_view, can_create, can_edit, can_delete, can_validate)
SELECT r.id, m.module, m.v, m.c, m.e, m.d, m.val
FROM roles r
CROSS JOIN (
    SELECT 'dashboard' AS module, 1 AS v, 0 AS c, 0 AS e, 0 AS d, 0 AS val
    UNION ALL SELECT 'pos',       1, 1, 1, 0, 1
    UNION ALL SELECT 'sales',     1, 1, 1, 0, 1
    UNION ALL SELECT 'customers', 1, 1, 1, 0, 0
    UNION ALL SELECT 'products',  1, 0, 0, 0, 0
    UNION ALL SELECT 'stock',     1, 0, 0, 0, 0
    UNION ALL SELECT 'cash',      1, 1, 1, 0, 1
) m
WHERE r.code = 'VENDEUR';

-- TECHNICIEN : SAV/reparations, pieces, consultation produits/stock.
INSERT INTO permissions (role_id, module, can_view, can_create, can_edit, can_delete, can_validate)
SELECT r.id, m.module, m.v, m.c, m.e, m.d, m.val
FROM roles r
CROSS JOIN (
    SELECT 'dashboard' AS module, 1 AS v, 0 AS c, 0 AS e, 0 AS d, 0 AS val
    UNION ALL SELECT 'repairs',   1, 1, 1, 0, 1
    UNION ALL SELECT 'products',  1, 0, 0, 0, 0
    UNION ALL SELECT 'stock',     1, 0, 1, 0, 0
) m
WHERE r.code = 'TECHNICIEN';

-- ------------------------------------------------------------- Catégories ---
INSERT INTO categories (name, parent_id) VALUES
    ('Téléphones', NULL),
    ('Accessoires', NULL),
    ('Pièces détachées', NULL),
    ('Outils informatiques', NULL);

-- ------------------------------------------------- Catégories de dépenses --
INSERT INTO expense_categories (name, description) VALUES
    ('Loyer', 'Loyer du local'),
    ('Salaires', 'Rémunération du personnel'),
    ('Électricité / Internet', 'Charges'),
    ('Transport', 'Livraisons et déplacements'),
    ('Fournitures', 'Consommables et petit matériel'),
    ('Autres', 'Dépenses diverses');

-- ---------------------------------------------- Paramètres par défaut ------
INSERT INTO settings (cle, valeur, groupe, type) VALUES
    ('shop.name', 'TechShop', 'magasin', 'STRING'),
    ('shop.address', 'Alger, Algérie', 'magasin', 'STRING'),
    ('shop.phone', '0XX XX XX XX XX', 'magasin', 'STRING'),
    ('shop.logo', '', 'magasin', 'STRING'),
    ('ticket.footer', 'Merci de votre confiance — Aucun échange sans ticket.', 'impression', 'STRING'),
    ('sale.vat_default', '19', 'ventes', 'DECIMAL'),
    ('sale.allow_negative_stock', '0', 'ventes', 'BOOLEAN'),
    ('stock.alert_threshold', '5', 'stock', 'INT'),
    ('apparence.theme', 'LIGHT', 'apparence', 'STRING'),
    ('apparence.font_size', '13', 'apparence', 'INT'),
    ('numbering.sale.prefix', 'V', 'ventes', 'STRING'),
    ('numbering.repair.prefix', 'R', 'service', 'STRING'),
    ('numbering.cash.prefix', 'C', 'caisse', 'STRING');
