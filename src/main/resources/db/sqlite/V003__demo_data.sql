-- ============================================================================
-- TechShop Manager - SQLite demo data (V003)
-- Profil demo uniquement. Le profil prod n'exécute jamais ce script.
-- Mot de passe des comptes de démonstration : admin123 (BCrypt, coût 10).
-- ============================================================================

-- ---------------------------------------------------------------- Comptes ---
INSERT OR IGNORE INTO users (username, password_hash, full_name, phone, active, must_change_password) VALUES
    ('admin', '$2a$10$VH5Os9q.7BXdmrdGynqpv.XLhWQvGClPvr7mrgKvv3AenFXdi3M8i',
     'Administrateur', NULL, 1, 1),
    ('tech1', '$2a$10$VH5Os9q.7BXdmrdGynqpv.XLhWQvGClPvr7mrgKvv3AenFXdi3M8i',
     'Karim Bensalem', '0551110001', 1, 0),
    ('tech2', '$2a$10$VH5Os9q.7BXdmrdGynqpv.XLhWQvGClPvr7mrgKvv3AenFXdi3M8i',
     'Yacine Terki', '0551110002', 1, 0),
    ('tech3', '$2a$10$VH5Os9q.7BXdmrdGynqpv.XLhWQvGClPvr7mrgKvv3AenFXdi3M8i',
     'Nadia Cherif', '0551110003', 1, 0);

-- ----------------------------------------------------------------- Marques ---
INSERT OR IGNORE INTO brands (name, active) VALUES
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
INSERT OR IGNORE INTO products (ref, barcode, name, description, category_id, brand_id, unit,
                                purchase_price, avg_cost, sale_price, vat_rate, alert_threshold,
                                quantity, track_imei, warranty_months, active)
SELECT s.ref, s.barcode, s.name, s.descr, c.id, b.id, s.unit,
       s.achat, s.achat, s.vente, 19.00, s.seuil, s.qte, s.imei, s.garantie, 1
FROM (
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
INSERT OR IGNORE INTO product_units (product_id, imei, serial_number, status, purchase_price, received_at)
SELECT p.id,
       printf('356000%04d%04d0', p.id, n.i),
       NULL, 'EN_STOCK', p.purchase_price, datetime('now', 'localtime')
FROM products p
JOIN (
    SELECT 1 AS i UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
    UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
    UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14
) n ON n.i <= p.quantity
WHERE p.track_imei = 1 AND p.active = 1;

-- -------------------------------------------------- Mouvements de stock -----
INSERT INTO stock_movements (product_id, type, quantity, quantity_before, quantity_after,
                             unit_cost, reason, reference_type, user_id, movement_date)
SELECT p.id, 'ENTREE', p.quantity, 0, p.quantity, p.purchase_price,
       'Stock initial', 'MANUAL', u.id, datetime('now', 'localtime')
FROM products p
CROSS JOIN (SELECT id FROM users WHERE username = 'admin' LIMIT 1) u
WHERE p.quantity > 0
  AND NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.product_id = p.id);

-- ------------------------------------------------------------ Fournisseurs --
INSERT OR IGNORE INTO suppliers (name, contact_name, phone, email, address, balance) VALUES
    ('Algerie Telecom Distribution', 'Mohamed Benali', '0560123456', 'contact@atd.dz', 'Alger', 0),
    ('Samsung Alger', 'Karim Hamidi', '0560234567', 'vente@samsung.dz', 'Alger', 0),
    ('Xiaomi Distribution DZ', 'Amine Boudiaf', '0560345678', 'dz@xiaomi.dz', 'Oran', 0),
    ('Apple Reseller DZ', 'Nadia Kaci', '0560456789', 'contact@apple.dz', 'Alger', 0),
    ('GSM Parts DZ', 'Rachid Ziani', '0560567890', 'parts@gsm.dz', 'Constantine', 0),
    ('Electron DZ', 'Farid Maouche', '0560678901', 'info@electron.dz', 'Alger', 0),
    ('Accessoires Pro', 'Sofiane Larbi', '0560789012', 'sales@accesspro.dz', 'Blida', 0),
    ('Outils IT DZ', 'Yacine Merabet', '0560890123', 'it@outils.dz', 'Alger', 0);

-- ---------------------------------------------------------------- Clients ---
INSERT OR IGNORE INTO customers (code, full_name, phone, balance, active) VALUES
    ('C-000001', 'Ahmed Benaissa', '0551000001', 0, 1),
    ('C-000002', 'Fatima Zohra', '0551000002', 0, 1),
    ('C-000003', 'Kamel Bouzid', '0551000003', 0, 1),
    ('C-000004', 'Samira Belkacem', '0551000004', 0, 1),
    ('C-000005', 'Mourad Hamlaoui', '0551000005', 0, 1),
    ('C-000006', 'Nabila Chentouf', '0551000006', 0, 1),
    ('C-000007', 'Djamel Aissani', '0551000007', 0, 1),
    ('C-000008', 'Leila Messaoudi', '0551000008', 0, 1),
    ('C-000009', 'Tarek Khelifi', '0551000009', 0, 1),
    ('C-000010', 'Sonia Meftah', '0551000010', 0, 1),
    ('C-000011', 'Hocine Rahmani', '0551000011', 0, 1),
    ('C-000012', 'Amina Benyahia', '0551000012', 0, 1),
    ('C-000013', 'Omar Zaidi', '0551000013', 0, 1),
    ('C-000014', 'Salima Bouali', '0551000014', 0, 1),
    ('C-000015', 'Chakib Merouani', '0551000015', 0, 1),
    ('C-000016', 'Naima Ouali', '0551000016', 0, 1),
    ('C-000017', 'Brahim Hadj', '0551000017', 0, 1),
    ('C-000018', 'Karima Ferhat', '0551000018', 0, 1),
    ('C-000019', 'Sidali Bouguerra', '0551000019', 0, 1),
    ('C-000020', 'Hayat Belhadj', '0551000020', 0, 1),
    ('C-000021', 'Reda Boudjema', '0551000021', 0, 1),
    ('C-000022', 'Djamila Saidi', '0551000022', 0, 1),
    ('C-000023', 'Mokhtar Guendouz', '0551000023', 0, 1),
    ('C-000024', 'Fatiha Maameri', '0551000024', 0, 1),
    ('C-000025', 'Abdelkader Benachour', '0551000025', 0, 1),
    ('C-000026', 'Souad Khaldi', '0551000026', 0, 1),
    ('C-000027', 'Hassan Benrabah', '0551000027', 0, 1),
    ('C-000028', 'Zohra Djebbar', '0551000028', 0, 1),
    ('C-000029', 'Mustapha Amrani', '0551000029', 0, 1),
    ('C-000030', 'Meriem Sellami', '0551000030', 0, 1),
    ('C-000031', 'Lamine Bouchareb', '0551000031', 0, 1),
    ('C-000032', 'Nesrine Chabane', '0551000032', 0, 1),
    ('C-000033', 'Yacine Tahar', '0551000033', 0, 1),
    ('C-000034', 'Houria Boutaleb', '0551000034', 0, 1),
    ('C-000035', 'Fares Kaddour', '0551000035', 0, 1),
    ('C-000036', 'Rima Belkadi', '0551000036', 0, 1),
    ('C-000037', 'Khaled Ait', '0551000037', 0, 1),
    ('C-000038', 'Sabrina Goumri', '0551000038', 0, 1),
    ('C-000039', 'Adel Meziane', '0551000039', 0, 1),
    ('C-000040', 'Warda Brahimi', '0551000040', 0, 1);

-- ------------------------------------------------------ Bons de commande ---
INSERT OR IGNORE INTO purchase_orders (number, supplier_id, order_date, status, subtotal, total, paid_amount, notes, user_id)
SELECT 'A-2026-000001', s.id, date('now', 'localtime'), 'ENVOYE', 32000, 32000, 0, 'Approvisionnements', u.id
FROM suppliers s CROSS JOIN users u WHERE s.name = 'Samsung Alger' AND u.username = 'admin' LIMIT 1;
INSERT OR IGNORE INTO purchase_orders (number, supplier_id, order_date, status, subtotal, total, paid_amount, notes, user_id)
SELECT 'A-2026-000002', s.id, date('now', 'localtime', '-1 day'), 'BROUILLON', 11000, 11000, 0, 'Câbles/accessoires', u.id
FROM suppliers s CROSS JOIN users u WHERE s.name = 'Accessoires Pro' AND u.username = 'admin' LIMIT 1;
INSERT OR IGNORE INTO purchase_orders (number, supplier_id, order_date, status, subtotal, total, paid_amount, notes, user_id)
SELECT 'A-2026-000003', s.id, date('now', 'localtime', '-2 days'), 'RECU', 28000, 28000, 28000, 'Note 13 reçue', u.id
FROM suppliers s CROSS JOIN users u WHERE s.name = 'Xiaomi Distribution DZ' AND u.username = 'admin' LIMIT 1;

-- ------------------------------------------------- Dossiers de réparation ---
INSERT INTO repair_tickets (number, customer_id, device_type, brand, model, serial_or_imei,
        device_password, accessories, physical_condition, reported_issue, diagnosis, quoted_amount,
        status, technician_id, received_at, promised_date, completed_at, labor_cost, parts_total,
        total, deposit_amount, paid_amount, due_amount, warranty_days) VALUES
('R-000001', (SELECT id FROM customers WHERE code='C-000001'), 'Telephone', 'Apple', 'iPhone 15', '351234567890001', '1234', 'Aucun', 'Rayures legeres sur la coque', 'Ecran fissure apres une chute', 'Ecran OLED a remplacer', 45000, 'RECU', (SELECT id FROM users WHERE username='tech1'), datetime('now','localtime','-2 days'), date('now','localtime','+3 days'), NULL, 0, 0, 0, 0, 0, 0, 90),
('R-000002', (SELECT id FROM customers WHERE code='C-000002'), 'Telephone', 'Samsung', 'Galaxy A54', '351234567890002', '0000', 'Aucun', 'Bon etat general', 'Ne charge plus du tout', 'Connecteur de charge a verifier', 8000, 'DIAGNOSTIC', (SELECT id FROM users WHERE username='tech2'), datetime('now','localtime','-4 days'), date('now','localtime','+2 days'), NULL, 0, 0, 0, 0, 0, 0, 90),
('R-000003', (SELECT id FROM customers WHERE code='C-000003'), 'Telephone', 'Xiaomi', 'Redmi Note 13', '351234567890003', '0000', 'Coque', 'Traces d usure', 'Batterie qui se decharge tres vite', 'Batterie a remplacer', 7000, 'DIAGNOSTIC', (SELECT id FROM users WHERE username='tech3'), datetime('now','localtime','-5 days'), date('now','localtime','+2 days'), NULL, 0, 0, 0, 0, 0, 0, 90),
('R-000004', (SELECT id FROM customers WHERE code='C-000004'), 'Telephone', 'Oppo', 'A78', '351234567890004', '0000', 'Aucun', 'Ecran legerement raye', 'Ecran tactile ne repond plus', 'Nappe tactile a controler', 10000, 'DIAGNOSTIC', (SELECT id FROM users WHERE username='tech1'), datetime('now','localtime','-9 days'), date('now','localtime','-2 days'), NULL, 0, 0, 0, 0, 0, 0, 30),
('R-000005', (SELECT id FROM customers WHERE code='C-000005'), 'Telephone', 'Apple', 'iPhone 13', '351234567890005', '1234', 'Aucun', 'Bon etat', 'Ecran casse en haut a droite', 'Ecran a remplacer (piece en commande)', 40000, 'ATTENTE_PIECE', (SELECT id FROM users WHERE username='tech2'), datetime('now','localtime','-6 days'), date('now','localtime','+1 day'), NULL, 0, 0, 0, 0, 0, 0, 90),
('R-000006', (SELECT id FROM customers WHERE code='C-000006'), 'Telephone', 'Samsung', 'Galaxy A15', '351234567890006', '0000', 'Aucun', 'Micro rayures', 'Ne charge plus', 'Nappe de charge a remplacer (commande fournisseur)', 4000, 'ATTENTE_PIECE', (SELECT id FROM users WHERE username='tech1'), datetime('now','localtime','-8 days'), date('now','localtime','-1 day'), NULL, 0, 0, 0, 0, 0, 0, 0),
('R-000007', (SELECT id FROM customers WHERE code='C-000007'), 'Telephone', 'Tecno', 'Spark 20', '351234567890007', '0000', 'Coque', 'Ecran casse', 'Ecran casse apres chute', 'Piece en attente de commande', 9000, 'ATTENTE_PIECE', (SELECT id FROM users WHERE username='tech3'), datetime('now','localtime','-7 days'), date('now','localtime','+3 days'), NULL, 0, 0, 0, 0, 0, 0, 0),
('R-000008', (SELECT id FROM customers WHERE code='C-000008'), 'Telephone', 'Huawei', 'P30 Lite', '351234567890008', '0000', 'Aucun', 'Bon etat', 'Ne charge plus', 'Connecteur de charge a remplacer', 6000, 'ATTENTE_PIECE', (SELECT id FROM users WHERE username='tech2'), datetime('now','localtime','-10 days'), date('now','localtime','-3 days'), NULL, 0, 0, 0, 0, 0, 0, 0),
('R-000009', (SELECT id FROM customers WHERE code='C-000009'), 'Telephone', 'Samsung', 'Galaxy A15', '351234567890009', '0000', 'Aucun', 'Chassis legerement deforme', 'Ecran fissure', 'Ecran LCD remplace', 12000, 'EN_REPARATION', (SELECT id FROM users WHERE username='tech1'), datetime('now','localtime','-5 days'), date('now','localtime','+2 days'), NULL, 3000, 12000, 15000, 5000, 5000, 10000, 90),
('R-000010', (SELECT id FROM customers WHERE code='C-000010'), 'Telephone', 'Xiaomi', 'Redmi 13C', '351234567890010', '0000', 'Coque', 'Bon etat', 'Appareil photo flou', 'Module camera arriere remplace', 3500, 'EN_REPARATION', (SELECT id FROM users WHERE username='tech3'), datetime('now','localtime','-4 days'), date('now','localtime','+1 day'), NULL, 2500, 3500, 6000, 2000, 2000, 4000, 30),
('R-000011', (SELECT id FROM customers WHERE code='C-000011'), 'Telephone', 'Apple', 'iPhone 15', '351234567890011', '1234', 'Aucun', 'Bon etat', 'Batterie gonflee', 'Batterie remplacee', 14000, 'EN_REPARATION', (SELECT id FROM users WHERE username='tech2'), datetime('now','localtime','-3 days'), date('now','localtime','+2 days'), NULL, 3500, 14000, 17500, 8000, 8000, 9500, 90),
('R-000012', (SELECT id FROM customers WHERE code='C-000012'), 'Telephone', 'Xiaomi', 'Redmi Note 13', '351234567890012', '0000', 'Coque', 'Micro rayures', 'Batterie HS', 'Batterie remplacee', 5500, 'EN_REPARATION', (SELECT id FROM users WHERE username='tech1'), datetime('now','localtime','-6 days'), date('now','localtime','+1 day'), NULL, 2500, 5500, 8000, 3000, 3000, 5000, 90),
('R-000013', (SELECT id FROM customers WHERE code='C-000013'), 'Telephone', 'Samsung', 'Galaxy A15', '351234567890013', '0000', 'Aucun', 'Bon etat', 'Ne charge plus', 'Nappe de charge remplacee', 2500, 'EN_REPARATION', (SELECT id FROM users WHERE username='tech2'), datetime('now','localtime','-2 days'), date('now','localtime','+2 days'), NULL, 2500, 2500, 5000, 0, 0, 5000, 30),
('R-000014', (SELECT id FROM customers WHERE code='C-000014'), 'Telephone', 'Samsung', 'Galaxy A15', '351234567890014', '0000', 'Coque', 'Ecran raye', 'Ecran casse', 'Reparation terminee, controle OK', 12000, 'PRETE', (SELECT id FROM users WHERE username='tech1'), datetime('now','localtime','-9 days'), date('now','localtime'), NULL, 3000, 12000, 15000, 5000, 5000, 10000, 90),
('R-000015', (SELECT id FROM customers WHERE code='C-000015'), 'Telephone', 'Xiaomi', 'Redmi Note 13', '351234567890015', '0000', 'Aucun', 'Bon etat', 'Batterie HS', 'Reparation terminee, pret au retrait', 5500, 'PRETE', (SELECT id FROM users WHERE username='tech3'), datetime('now','localtime','-12 days'), date('now','localtime','-1 day'), NULL, 2500, 5500, 8000, 0, 0, 8000, 90),
('R-000016', (SELECT id FROM customers WHERE code='C-000016'), 'Telephone', 'Samsung', 'Galaxy A15', '351234567890016', '0000', 'Aucun', 'Bon etat', 'Ne charge plus', 'Reparation terminee', 2500, 'PRETE', (SELECT id FROM users WHERE username='tech2'), datetime('now','localtime','-6 days'), date('now','localtime','+1 day'), NULL, 2000, 2500, 4500, 2000, 2000, 2500, 30),
('R-000017', (SELECT id FROM customers WHERE code='C-000017'), 'Telephone', 'Xiaomi', 'Redmi 13C', '351234567890017', '0000', 'Coque', 'Bon etat', 'Appareil photo flou', 'Camera remplacee, pret au retrait', 3500, 'PRETE', (SELECT id FROM users WHERE username='tech1'), datetime('now','localtime','-14 days'), date('now','localtime','-4 days'), NULL, 2500, 3500, 6000, 3000, 3000, 3000, 30),
('R-000018', (SELECT id FROM customers WHERE code='C-000018'), 'Telephone', 'Samsung', 'Galaxy A15', '351234567890018', '0000', 'Aucun', 'Bon etat', 'Ne charge plus', 'Nappe remplacee, appareil livre', 2500, 'LIVREE', (SELECT id FROM users WHERE username='tech3'), datetime('now','localtime','-20 days'), date('now','localtime','-12 days'), datetime('now','localtime','-11 days'), 2000, 2500, 4500, 2000, 4500, 0, 90),
('R-000019', (SELECT id FROM customers WHERE code='C-000019'), 'Telephone', 'Apple', 'iPhone 15', '351234567890019', '1234', 'Aucun', 'Bon etat', 'Batterie gonflee', 'Batterie remplacee, appareil livre', 14000, 'LIVREE', (SELECT id FROM users WHERE username='tech1'), datetime('now','localtime','-25 days'), date('now','localtime','-14 days'), datetime('now','localtime','-13 days'), 3500, 14000, 17500, 8000, 17500, 0, 90),
('R-000020', (SELECT id FROM customers WHERE code='C-000020'), 'Telephone', 'Samsung', 'Galaxy A15', '351234567890020', '0000', 'Coque', 'Chassis deforme', 'Ecran fissure', 'Ecran remplace, appareil livre', 12000, 'LIVREE', (SELECT id FROM users WHERE username='tech2'), datetime('now','localtime','-22 days'), date('now','localtime','-8 days'), datetime('now','localtime','-7 days'), 3000, 12000, 15000, 5000, 15000, 0, 90),
('R-000021', (SELECT id FROM customers WHERE code='C-000001'), 'Telephone', 'Apple', 'iPhone 13', '351234567890021', '1234', 'Aucun', 'Bon etat', 'Speaker grisaille', 'Haut-parleur nettoye, appareil livre', 2500, 'LIVREE', (SELECT id FROM users WHERE username='tech1'), datetime('now','localtime','-18 days'), date('now','localtime','-6 days'), datetime('now','localtime','-5 days'), 2500, 0, 2500, 0, 2500, 0, 30),
('R-000022', (SELECT id FROM customers WHERE code='C-000002'), 'Telephone', 'Samsung', 'Galaxy S23', '351234567890022', '0000', 'Aucun', 'Bon etat', 'Ecran casse', 'A diagnostiquer', 55000, 'RECU', (SELECT id FROM users WHERE username='tech3'), datetime('now','localtime','-1 day'), date('now','localtime','+3 days'), NULL, 0, 0, 0, 0, 0, 0, 90),
('R-000023', (SELECT id FROM customers WHERE code='C-000003'), 'Telephone', 'Apple', 'iPhone 15', '351234567890023', '1234', 'Aucun', 'Rayures', 'Ne demarre plus', 'Diagnostic en cours', 30000, 'DIAGNOSTIC', (SELECT id FROM users WHERE username='tech1'), datetime('now','localtime','-11 days'), date('now','localtime','-2 days'), NULL, 0, 0, 0, 0, 0, 0, 90),
('R-000024', (SELECT id FROM customers WHERE code='C-000004'), 'Telephone', 'Xiaomi', 'Redmi Note 13', '351234567890024', '0000', 'Coque', 'Bon etat', 'Ecran fissure', 'A diagnostiquer', 12000, 'RECU', (SELECT id FROM users WHERE username='tech2'), datetime('now','localtime','-2 days'), date('now','localtime','+4 days'), NULL, 0, 0, 0, 0, 0, 0, 90),
('R-000025', (SELECT id FROM customers WHERE code='C-000005'), 'Telephone', 'Tecno', 'Spark 20', '351234567890025', '0000', 'Aucun', 'Ecran casse', 'Ecran casse', 'Client ne souhaite plus reparer', 9000, 'ANNULEE', (SELECT id FROM users WHERE username='tech2'), datetime('now','localtime','-15 days'), date('now','localtime','-5 days'), datetime('now','localtime','-13 days'), 0, 0, 0, 0, 0, 0, 0);

-- ----------------------------------------------------- Chronologie demo -----
INSERT INTO repair_status_history (repair_id, old_status, new_status, comment, user_id, changed_at)
SELECT r.id, NULL, 'RECU', 'Depot de l appareil', COALESCE(r.technician_id, 1), r.received_at
FROM repair_tickets r WHERE r.number LIKE 'R-%';

INSERT INTO repair_status_history (repair_id, old_status, new_status, comment, user_id, changed_at)
SELECT r.id, 'RECU', r.status, 'Suivi du dossier', COALESCE(r.technician_id, 1),
       datetime(r.received_at, '+1 day')
FROM repair_tickets r WHERE r.number LIKE 'R-%' AND r.status <> 'RECU';

-- ------------------------------------------------------- Pieces consommees --
INSERT INTO repair_parts (repair_id, product_id, quantity, unit_cost, unit_price, total, user_id)
SELECT r.id, p.id, 1, 7500, 12000, 12000, COALESCE(r.technician_id, 1)
FROM repair_tickets r JOIN products p ON p.ref = 'P-000117'
WHERE r.number IN ('R-000009', 'R-000014', 'R-000020');

INSERT INTO repair_parts (repair_id, product_id, quantity, unit_cost, unit_price, total, user_id)
SELECT r.id, p.id, 1, 1800, 3500, 3500, COALESCE(r.technician_id, 1)
FROM repair_tickets r JOIN products p ON p.ref = 'P-000121'
WHERE r.number IN ('R-000010', 'R-000017');

INSERT INTO repair_parts (repair_id, product_id, quantity, unit_cost, unit_price, total, user_id)
SELECT r.id, p.id, 1, 8500, 14000, 14000, COALESCE(r.technician_id, 1)
FROM repair_tickets r JOIN products p ON p.ref = 'P-000120'
WHERE r.number IN ('R-000011', 'R-000019');

INSERT INTO repair_parts (repair_id, product_id, quantity, unit_cost, unit_price, total, user_id)
SELECT r.id, p.id, 1, 3200, 5500, 5500, COALESCE(r.technician_id, 1)
FROM repair_tickets r JOIN products p ON p.ref = 'P-000118'
WHERE r.number IN ('R-000012', 'R-000015');

INSERT INTO repair_parts (repair_id, product_id, quantity, unit_cost, unit_price, total, user_id)
SELECT r.id, p.id, 1, 1200, 2500, 2500, COALESCE(r.technician_id, 1)
FROM repair_tickets r JOIN products p ON p.ref = 'P-000119'
WHERE r.number IN ('R-000013', 'R-000016', 'R-000018');

-- --------------------------------------------------------------- Reglements --
INSERT INTO repair_payments (repair_id, payment_date, method, amount, note, user_id)
SELECT r.id, datetime(r.received_at, '+1 day'), 'ESPECES', r.deposit_amount, 'Acompte au depot',
       COALESCE(r.technician_id, 1)
FROM repair_tickets r WHERE r.deposit_amount > 0;

INSERT INTO repair_payments (repair_id, payment_date, method, amount, note, user_id)
SELECT r.id, r.completed_at, 'ESPECES', (r.paid_amount - r.deposit_amount), 'Reglement du solde',
       COALESCE(r.technician_id, 1)
FROM repair_tickets r WHERE r.paid_amount > r.deposit_amount AND r.completed_at IS NOT NULL;
