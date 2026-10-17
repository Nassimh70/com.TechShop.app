-- ============================================================================
-- TechShop Manager - V006__phase5.sql
-- Phase 5 (service apres-vente, reparations - SPEC 4.9 et 5) :
--   * colonne « quoted_amount » (devis estime communique au client) absente du
--     schema initial ;
--   * techniciens de demonstration ;
--   * 25 dossiers de demonstration repartis sur tous les statuts, avec leur
--     chronologie, des pieces consommees et des reglements.
-- Les scripts sont decoupes par instruction « ; » : aucun point-virgule dans les
-- libelles. Les identifiants sont resolus par sous-requete (code client,
-- identifiant utilisateur) pour rester valables quelle que soit la base.
-- ============================================================================

-- --------------------------------------------------- Devis estime (colonne) --
ALTER TABLE repair_tickets
    ADD COLUMN quoted_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Devis estime' AFTER diagnosis;

-- ------------------------------------------------------- Techniciens demo ---
-- Mot de passe haché en BCrypt (identique a l'admin initial : admin123).
INSERT INTO users (username, password_hash, full_name, phone, role_id, active, must_change_password)
SELECT 'tech1', '$2a$10$VH5Os9q.7BXdmrdGynqpv.XLhWQvGClPvr7mrgKvv3AenFXdi3M8i',
       'Karim Bensalem', '0551110001', r.id, 1, 0
FROM roles r WHERE r.code = 'TECHNICIEN';

INSERT INTO users (username, password_hash, full_name, phone, role_id, active, must_change_password)
SELECT 'tech2', '$2a$10$VH5Os9q.7BXdmrdGynqpv.XLhWQvGClPvr7mrgKvv3AenFXdi3M8i',
       'Yacine Terki', '0551110002', r.id, 1, 0
FROM roles r WHERE r.code = 'TECHNICIEN';

INSERT INTO users (username, password_hash, full_name, phone, role_id, active, must_change_password)
SELECT 'tech3', '$2a$10$VH5Os9q.7BXdmrdGynqpv.XLhWQvGClPvr7mrgKvv3AenFXdi3M8i',
       'Nadia Cherif', '0551110003', r.id, 1, 0
FROM roles r WHERE r.code = 'TECHNICIEN';

-- ------------------------------------------------- Dossiers de reparation ---
INSERT INTO repair_tickets (number, customer_id, device_type, brand, model, serial_or_imei,
        device_password, accessories, physical_condition, reported_issue, diagnosis, quoted_amount,
        status, technician_id, received_at, promised_date, completed_at, labor_cost, parts_total,
        total, deposit_amount, paid_amount, due_amount, warranty_days) VALUES
('R-000001', (SELECT id FROM customers WHERE code='C-000001'), 'Telephone', 'Apple', 'iPhone 15', '351234567890001', '1234', 'Aucun', 'Rayures legeres sur la coque', 'Ecran fissure apres une chute', 'Ecran OLED a remplacer', 45000, 'RECU', (SELECT id FROM users WHERE username='tech1'), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(CURDATE(), INTERVAL 3 DAY), NULL, 0, 0, 0, 0, 0, 0, 90),
('R-000002', (SELECT id FROM customers WHERE code='C-000002'), 'Telephone', 'Samsung', 'Galaxy A54', '351234567890002', '0000', 'Aucun', 'Bon etat general', 'Ne charge plus du tout', 'Connecteur de charge a verifier', 8000, 'DIAGNOSTIC', (SELECT id FROM users WHERE username='tech2'), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_ADD(CURDATE(), INTERVAL 2 DAY), NULL, 0, 0, 0, 0, 0, 0, 90),
('R-000003', (SELECT id FROM customers WHERE code='C-000003'), 'Telephone', 'Xiaomi', 'Redmi Note 13', '351234567890003', '0000', 'Coque', 'Traces d usure', 'Batterie qui se decharge tres vite', 'Batterie a remplacer', 7000, 'DIAGNOSTIC', (SELECT id FROM users WHERE username='tech3'), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(CURDATE(), INTERVAL 2 DAY), NULL, 0, 0, 0, 0, 0, 0, 90),
('R-000004', (SELECT id FROM customers WHERE code='C-000004'), 'Telephone', 'Oppo', 'A78', '351234567890004', '0000', 'Aucun', 'Ecran legerement raye', 'Ecran tactile ne repond plus', 'Nappe tactile a controler', 10000, 'DIAGNOSTIC', (SELECT id FROM users WHERE username='tech1'), DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(CURDATE(), INTERVAL 2 DAY), NULL, 0, 0, 0, 0, 0, 0, 30),
('R-000005', (SELECT id FROM customers WHERE code='C-000005'), 'Telephone', 'Apple', 'iPhone 13', '351234567890005', '1234', 'Aucun', 'Bon etat', 'Ecran casse en haut a droite', 'Ecran a remplacer (piece en commande)', 40000, 'ATTENTE_PIECE', (SELECT id FROM users WHERE username='tech2'), DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_ADD(CURDATE(), INTERVAL 1 DAY), NULL, 0, 0, 0, 0, 0, 0, 90),
('R-000006', (SELECT id FROM customers WHERE code='C-000006'), 'Telephone', 'Samsung', 'Galaxy A15', '351234567890006', '0000', 'Aucun', 'Micro rayures', 'Ne charge plus', 'Nappe de charge a remplacer (commande fournisseur)', 4000, 'ATTENTE_PIECE', (SELECT id FROM users WHERE username='tech1'), DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(CURDATE(), INTERVAL 1 DAY), NULL, 0, 0, 0, 0, 0, 0, 0),
('R-000007', (SELECT id FROM customers WHERE code='C-000007'), 'Telephone', 'Tecno', 'Spark 20', '351234567890007', '0000', 'Coque', 'Ecran casse', 'Ecran casse apres chute', 'Piece en attente de commande', 9000, 'ATTENTE_PIECE', (SELECT id FROM users WHERE username='tech3'), DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_ADD(CURDATE(), INTERVAL 3 DAY), NULL, 0, 0, 0, 0, 0, 0, 0),
('R-000008', (SELECT id FROM customers WHERE code='C-000008'), 'Telephone', 'Huawei', 'P30 Lite', '351234567890008', '0000', 'Aucun', 'Bon etat', 'Ne charge plus', 'Connecteur de charge a remplacer', 6000, 'ATTENTE_PIECE', (SELECT id FROM users WHERE username='tech2'), DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(CURDATE(), INTERVAL 3 DAY), NULL, 0, 0, 0, 0, 0, 0, 0),
('R-000009', (SELECT id FROM customers WHERE code='C-000009'), 'Telephone', 'Samsung', 'Galaxy A15', '351234567890009', '0000', 'Aucun', 'Chassis legerement deforme', 'Ecran fissure', 'Ecran LCD remplace', 12000, 'EN_REPARATION', (SELECT id FROM users WHERE username='tech1'), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(CURDATE(), INTERVAL 2 DAY), NULL, 3000, 12000, 15000, 5000, 5000, 10000, 90),
('R-000010', (SELECT id FROM customers WHERE code='C-000010'), 'Telephone', 'Xiaomi', 'Redmi 13C', '351234567890010', '0000', 'Coque', 'Bon etat', 'Appareil photo flou', 'Module camera arriere remplace', 3500, 'EN_REPARATION', (SELECT id FROM users WHERE username='tech3'), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_ADD(CURDATE(), INTERVAL 1 DAY), NULL, 2500, 3500, 6000, 2000, 2000, 4000, 30),
('R-000011', (SELECT id FROM customers WHERE code='C-000011'), 'Telephone', 'Apple', 'iPhone 15', '351234567890011', '1234', 'Aucun', 'Bon etat', 'Batterie gonflee', 'Batterie remplacee', 14000, 'EN_REPARATION', (SELECT id FROM users WHERE username='tech2'), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 2 DAY), NULL, 3500, 14000, 17500, 8000, 8000, 9500, 90),
('R-000012', (SELECT id FROM customers WHERE code='C-000012'), 'Telephone', 'Xiaomi', 'Redmi Note 13', '351234567890012', '0000', 'Coque', 'Micro rayures', 'Batterie HS', 'Batterie remplacee', 5500, 'EN_REPARATION', (SELECT id FROM users WHERE username='tech1'), DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_ADD(CURDATE(), INTERVAL 1 DAY), NULL, 2500, 5500, 8000, 3000, 3000, 5000, 90),
('R-000013', (SELECT id FROM customers WHERE code='C-000013'), 'Telephone', 'Samsung', 'Galaxy A15', '351234567890013', '0000', 'Aucun', 'Bon etat', 'Ne charge plus', 'Nappe de charge remplacee', 2500, 'EN_REPARATION', (SELECT id FROM users WHERE username='tech2'), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(CURDATE(), INTERVAL 2 DAY), NULL, 2500, 2500, 5000, 0, 0, 5000, 30),
('R-000014', (SELECT id FROM customers WHERE code='C-000014'), 'Telephone', 'Samsung', 'Galaxy A15', '351234567890014', '0000', 'Coque', 'Ecran raye', 'Ecran casse', 'Reparation terminee, controle OK', 12000, 'PRETE', (SELECT id FROM users WHERE username='tech1'), DATE_SUB(NOW(), INTERVAL 9 DAY), CURDATE(), NULL, 3000, 12000, 15000, 5000, 5000, 10000, 90),
('R-000015', (SELECT id FROM customers WHERE code='C-000015'), 'Telephone', 'Xiaomi', 'Redmi Note 13', '351234567890015', '0000', 'Aucun', 'Bon etat', 'Batterie HS', 'Reparation terminee, pret au retrait', 5500, 'PRETE', (SELECT id FROM users WHERE username='tech3'), DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(CURDATE(), INTERVAL 1 DAY), NULL, 2500, 5500, 8000, 0, 0, 8000, 90),
('R-000016', (SELECT id FROM customers WHERE code='C-000016'), 'Telephone', 'Samsung', 'Galaxy A15', '351234567890016', '0000', 'Aucun', 'Bon etat', 'Ne charge plus', 'Reparation terminee', 2500, 'PRETE', (SELECT id FROM users WHERE username='tech2'), DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_ADD(CURDATE(), INTERVAL 1 DAY), NULL, 2000, 2500, 4500, 2000, 2000, 2500, 30),
('R-000017', (SELECT id FROM customers WHERE code='C-000017'), 'Telephone', 'Xiaomi', 'Redmi 13C', '351234567890017', '0000', 'Coque', 'Bon etat', 'Appareil photo flou', 'Camera remplacee, pret au retrait', 3500, 'PRETE', (SELECT id FROM users WHERE username='tech1'), DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(CURDATE(), INTERVAL 4 DAY), NULL, 2500, 3500, 6000, 3000, 3000, 3000, 30),
('R-000018', (SELECT id FROM customers WHERE code='C-000018'), 'Telephone', 'Samsung', 'Galaxy A15', '351234567890018', '0000', 'Aucun', 'Bon etat', 'Ne charge plus', 'Nappe remplacee, appareil livre', 2500, 'LIVREE', (SELECT id FROM users WHERE username='tech3'), DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY), 2000, 2500, 4500, 2000, 4500, 0, 90),
('R-000019', (SELECT id FROM customers WHERE code='C-000019'), 'Telephone', 'Apple', 'iPhone 15', '351234567890019', '1234', 'Aucun', 'Bon etat', 'Batterie gonflee', 'Batterie remplacee, appareil livre', 14000, 'LIVREE', (SELECT id FROM users WHERE username='tech1'), DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(CURDATE(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY), 3500, 14000, 17500, 8000, 17500, 0, 90),
('R-000020', (SELECT id FROM customers WHERE code='C-000020'), 'Telephone', 'Samsung', 'Galaxy A15', '351234567890020', '0000', 'Coque', 'Chassis deforme', 'Ecran fissure', 'Ecran remplace, appareil livre', 12000, 'LIVREE', (SELECT id FROM users WHERE username='tech2'), DATE_SUB(NOW(), INTERVAL 22 DAY), DATE_SUB(CURDATE(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), 3000, 12000, 15000, 5000, 15000, 0, 90),
('R-000021', (SELECT id FROM customers WHERE code='C-000001'), 'Telephone', 'Apple', 'iPhone 13', '351234567890021', '1234', 'Aucun', 'Bon etat', 'Speaker grisaille', 'Haut-parleur nettoye, appareil livre', 2500, 'LIVREE', (SELECT id FROM users WHERE username='tech1'), DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(CURDATE(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 2500, 0, 2500, 0, 2500, 0, 30),
('R-000022', (SELECT id FROM customers WHERE code='C-000002'), 'Telephone', 'Samsung', 'Galaxy S23', '351234567890022', '0000', 'Aucun', 'Bon etat', 'Ecran casse', 'A diagnostiquer', 55000, 'RECU', (SELECT id FROM users WHERE username='tech3'), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 3 DAY), NULL, 0, 0, 0, 0, 0, 0, 90),
('R-000023', (SELECT id FROM customers WHERE code='C-000003'), 'Telephone', 'Apple', 'iPhone 15', '351234567890023', '1234', 'Aucun', 'Rayures', 'Ne demarre plus', 'Diagnostic en cours', 30000, 'DIAGNOSTIC', (SELECT id FROM users WHERE username='tech1'), DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(CURDATE(), INTERVAL 2 DAY), NULL, 0, 0, 0, 0, 0, 0, 90),
('R-000024', (SELECT id FROM customers WHERE code='C-000004'), 'Telephone', 'Xiaomi', 'Redmi Note 13', '351234567890024', '0000', 'Coque', 'Bon etat', 'Ecran fissure', 'A diagnostiquer', 12000, 'RECU', (SELECT id FROM users WHERE username='tech2'), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(CURDATE(), INTERVAL 4 DAY), NULL, 0, 0, 0, 0, 0, 0, 90),
('R-000025', (SELECT id FROM customers WHERE code='C-000005'), 'Telephone', 'Tecno', 'Spark 20', '351234567890025', '0000', 'Aucun', 'Ecran casse', 'Ecran casse', 'Client ne souhaite plus reparer', 9000, 'ANNULEE', (SELECT id FROM users WHERE username='tech2'), DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(CURDATE(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY), 0, 0, 0, 0, 0, 0, 0);

-- ----------------------------------------------------- Chronologie demo -----
INSERT INTO repair_status_history (repair_id, old_status, new_status, comment, user_id, changed_at)
SELECT r.id, NULL, 'RECU', 'Depot de l appareil', COALESCE(r.technician_id, 1), r.received_at
FROM repair_tickets r WHERE r.number LIKE 'R-%';

INSERT INTO repair_status_history (repair_id, old_status, new_status, comment, user_id, changed_at)
SELECT r.id, 'RECU', r.status, 'Suivi du dossier', COALESCE(r.technician_id, 1),
       DATE_ADD(r.received_at, INTERVAL 1 DAY)
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
SELECT r.id, DATE_ADD(r.received_at, INTERVAL 1 DAY), 'ESPECES', r.deposit_amount, 'Acompte au depot',
       COALESCE(r.technician_id, 1)
FROM repair_tickets r WHERE r.deposit_amount > 0;

INSERT INTO repair_payments (repair_id, payment_date, method, amount, note, user_id)
SELECT r.id, r.completed_at, 'ESPECES', (r.paid_amount - r.deposit_amount), 'Reglement du solde',
       COALESCE(r.technician_id, 1)
FROM repair_tickets r WHERE r.paid_amount > r.deposit_amount AND r.completed_at IS NOT NULL;
