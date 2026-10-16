-- ============================================================================
-- TechShop Manager - V003__product_seed.sql
-- Jeu de données de démonstration pour le catalogue (SPEC §4.5) et le stock
-- (SPEC §4.6) :
--   * marques
--   * produits (téléphones, accessoires, pièces détachées, outils)
--   * appareils sérialisés (IMEI) pour les produits suivis par IMEI
--   * mouvements de stock initiaux (entrées de stock)
-- Les INSERT sont tolérants (IGNORE) afin de pouvoir rejouer le script sans
-- erreur si certaines lignes existent déjà. Aucun point-virgule dans les
-- libellés de données (les scripts sont découpés par instruction SQL).
-- ============================================================================

-- ----------------------------------------------------------------- Marques ---
INSERT IGNORE INTO brands (name, active) VALUES
    ('Samsung', 1),
    ('Xiaomi', 1),
    ('Apple', 1),
    ('Oppo', 1),
    ('Tecno', 1),
    ('Huawei', 1),
    ('Anker', 1),
    ('SanDisk', 1),
    ('Générique', 1);

-- ---------------------------------------------------------------- Produits ---
INSERT IGNORE INTO products (ref, barcode, name, description, category_id, brand_id, unit,
                             purchase_price, avg_cost, sale_price, vat_rate, alert_threshold,
                             quantity, track_imei, warranty_months, active)
SELECT s.ref, s.barcode, s.name, s.descr, c.id, b.id, s.unit,
       s.achat, s.achat, s.vente, 19.00, s.seuil, s.qte, s.imei, s.garantie, 1
FROM (
    -- Téléphones
    SELECT 'P-000101' AS ref, '2000000000101' AS barcode,
           'Samsung Galaxy A15 128 Go' AS name,
           'Smartphone 6,5 pouces 128 Go 4 Go de RAM double SIM' AS descr,
           'Téléphones' AS cat, 'Samsung' AS marque, 'pièce' AS unit,
           32000.00 AS achat, 39900.00 AS vente, 3 AS seuil, 6 AS qte, 1 AS imei, 12 AS garantie
    UNION ALL SELECT 'P-000102', '2000000000102', 'Samsung Galaxy S24 256 Go',
           'Smartphone haut de gamme 256 Go 8 Go de RAM', 'Téléphones', 'Samsung', 'pièce',
           118000.00, 139900.00, 2, 0, 1, 24
    UNION ALL SELECT 'P-000103', '2000000000103', 'Xiaomi Redmi Note 13 128 Go',
           'Smartphone 6,67 pouces 128 Go', 'Téléphones', 'Xiaomi', 'pièce',
           28000.00, 34900.00, 4, 10, 1, 12
    UNION ALL SELECT 'P-000104', '2000000000104', 'Xiaomi Redmi 13C 64 Go',
           'Smartphone d entrée de gamme 64 Go', 'Téléphones', 'Xiaomi', 'pièce',
           17000.00, 21900.00, 5, 12, 1, 12
    UNION ALL SELECT 'P-000105', '2000000000105', 'iPhone 15 128 Go',
           'Smartphone Apple 128 Go', 'Téléphones', 'Apple', 'pièce',
           145000.00, 169900.00, 2, 2, 1, 24
    UNION ALL SELECT 'P-000106', '2000000000106', 'iPhone 13 128 Go',
           'Smartphone Apple 128 Go', 'Téléphones', 'Apple', 'pièce',
           128000.00, 149900.00, 2, 1, 1, 24
    UNION ALL SELECT 'P-000107', '2000000000107', 'Oppo A18 64 Go',
           'Smartphone 6,56 pouces 64 Go', 'Téléphones', 'Oppo', 'pièce',
           19500.00, 24900.00, 4, 7, 1, 12
    UNION ALL SELECT 'P-000108', '2000000000108', 'Tecno Spark Go 2024 64 Go',
           'Smartphone 64 Go double SIM', 'Téléphones', 'Tecno', 'pièce',
           15500.00, 19900.00, 5, 9, 1, 12
    -- Accessoires
    UNION ALL SELECT 'P-000109', '2000000000109', 'Chargeur secteur 25 W USB-C',
           'Chargeur rapide 25 W', 'Accessoires', 'Samsung', 'pièce',
           2500.00, 3900.00, 5, 14, 0, 6
    UNION ALL SELECT 'P-000110', '2000000000110', 'Câble USB-C 3 A 1 m',
           'Câble de charge et de données 1 mètre', 'Accessoires', 'Générique', 'pièce',
           350.00, 900.00, 10, 42, 0, 0
    UNION ALL SELECT 'P-000111', '2000000000111', 'Écouteurs filaires 3,5 mm',
           'Écouteurs avec micro et télécommande', 'Accessoires', 'Générique', 'pièce',
           300.00, 800.00, 10, 35, 0, 0
    UNION ALL SELECT 'P-000112', '2000000000112', 'Écouteurs Bluetooth TWS',
           'Écouteurs sans fil avec boîtier de charge', 'Accessoires', 'Xiaomi', 'pièce',
           3200.00, 4900.00, 4, 8, 0, 6
    UNION ALL SELECT 'P-000113', '2000000000113', 'Chargeur USB-C vers Lightning 20 W',
           'Chargeur Apple 20 W connecteur USB-C', 'Accessoires', 'Apple', 'pièce',
           4500.00, 6900.00, 3, 3, 0, 6
    UNION ALL SELECT 'P-000114', '2000000000114', 'Powerbank 10000 mAh',
           'Batterie externe 10000 mAh double sortie', 'Accessoires', 'Anker', 'pièce',
           3600.00, 5500.00, 4, 7, 0, 12
    UNION ALL SELECT 'P-000115', '2000000000115', 'Protection écran verre trempé',
           'Verre trempé 9H toutes tailles', 'Accessoires', 'Générique', 'pièce',
           200.00, 700.00, 10, 60, 0, 0
    UNION ALL SELECT 'P-000116', '2000000000116', 'Coque de protection universelle',
           'Coque silicone renforcée', 'Accessoires', 'Générique', 'pièce',
           250.00, 900.00, 10, 48, 0, 0
    -- Pièces détachées
    UNION ALL SELECT 'P-000117', '2000000000117', 'Écran LCD compatible Galaxy A15',
           'Écran complet avec vitre tactile', 'Pièces détachées', 'Samsung', 'pièce',
           7500.00, 12000.00, 3, 5, 0, 3
    UNION ALL SELECT 'P-000118', '2000000000118', 'Batterie Redmi Note 13',
           'Batterie de remplacement 5000 mAh', 'Pièces détachées', 'Xiaomi', 'pièce',
           3200.00, 5500.00, 3, 6, 0, 3
    UNION ALL SELECT 'P-000119', '2000000000119', 'Nappe de charge Galaxy A15',
           'Connecteur de charge USB-C avec nappe', 'Pièces détachées', 'Samsung', 'pièce',
           1200.00, 2500.00, 4, 9, 0, 0
    UNION ALL SELECT 'P-000120', '2000000000120', 'Batterie iPhone 15',
           'Batterie de remplacement', 'Pièces détachées', 'Apple', 'pièce',
           8500.00, 14000.00, 2, 0, 0, 3
    UNION ALL SELECT 'P-000121', '2000000000121', 'Caméra arrière Redmi 13C',
           'Module caméra arrière', 'Pièces détachées', 'Xiaomi', 'pièce',
           1800.00, 3500.00, 3, 4, 0, 0
    -- Outils informatiques
    UNION ALL SELECT 'P-000122', '2000000000122', 'Clé USB 32 Go',
           'Clé USB 3.0 32 Go', 'Outils informatiques', 'SanDisk', 'pièce',
           1100.00, 1900.00, 5, 18, 0, 12
    UNION ALL SELECT 'P-000123', '2000000000123', 'Souris optique USB',
           'Souris filaire 1000 dpi', 'Outils informatiques', 'Générique', 'pièce',
           700.00, 1500.00, 5, 11, 0, 6
    UNION ALL SELECT 'P-000124', '2000000000124', 'Clavier USB AZERTY',
           'Clavier filaire AZERTY', 'Outils informatiques', 'Générique', 'pièce',
           1600.00, 2900.00, 4, 6, 0, 6
    UNION ALL SELECT 'P-000125', '2000000000125', 'Casque filaire USB',
           'Casque stéréo avec micro', 'Outils informatiques', 'Générique', 'pièce',
           1300.00, 2400.00, 4, 5, 0, 6
) s
LEFT JOIN categories c ON c.name = s.cat
LEFT JOIN brands b ON b.name = s.marque;

-- ------------------------------------------- Appareils sérialisés (IMEI) -----
-- Un appareil par unité en stock des produits suivis par IMEI.
INSERT IGNORE INTO product_units (product_id, imei, serial_number, status, purchase_price, received_at)
SELECT p.id,
       CONCAT('356000', LPAD(p.id, 4, '0'), LPAD(n.i, 4, '0'), '0'),
       NULL, 'EN_STOCK', p.purchase_price, NOW()
FROM products p
JOIN (
    SELECT 1 AS i UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
    UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
    UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14
) n ON n.i <= p.quantity
WHERE p.track_imei = 1 AND p.active = 1;

-- -------------------------------------------------- Mouvements de stock -----
-- Entrée initiale correspondant à la quantité en cache de chaque produit.
INSERT INTO stock_movements (product_id, type, quantity, quantity_before, quantity_after,
                             unit_cost, reason, reference_type, user_id, movement_date)
SELECT p.id, 'ENTREE', p.quantity, 0, p.quantity, p.purchase_price,
       'Stock initial', 'MANUAL', u.id, NOW()
FROM products p
CROSS JOIN (SELECT id FROM users WHERE username = 'admin' LIMIT 1) u
WHERE p.quantity > 0
  AND NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.product_id = p.id);
