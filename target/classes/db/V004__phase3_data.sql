-- V004 : données démonstration phase 3
INSERT IGNORE INTO suppliers (name, contact_name, phone, email, address, balance) VALUES
('Algerie Telecom Distribution', 'Mohamed Benali', '0560123456', 'contact@atd.dz', 'Alger', 0),
('Samsung Alger', 'Karim Hamidi', '0560234567', 'vente@samsung.dz', 'Alger', 0),
('Xiaomi Distribution DZ', 'Amine Boudiaf', '0560345678', 'dz@xiaomi.dz', 'Oran', 0),
('Apple Reseller DZ', 'Nadia Kaci', '0560456789', 'contact@apple.dz', 'Alger', 0),
('GSM Parts DZ', 'Rachid Ziani', '0560567890', 'parts@gsm.dz', 'Constantine', 0),
('Electron DZ', 'Farid Maouche', '0560678901', 'info@electron.dz', 'Alger', 0),
('Accessoires Pro', 'Sofiane Larbi', '0560789012', 'sales@accesspro.dz', 'Blida', 0),
('Outils IT DZ', 'Yacine Merabet', '0560890123', 'it@outils.dz', 'Alger', 0);

INSERT IGNORE INTO customers (code, full_name, phone, balance, active) VALUES
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

INSERT IGNORE INTO purchase_orders (number, supplier_id, order_date, status, subtotal, total, paid_amount, notes, user_id)
SELECT 'A-2026-000001', s.id, CURDATE(), 'ENVOYE', 32000, 32000, 0, 'Approvisionnements', u.id
FROM suppliers s CROSS JOIN users u WHERE s.name='Samsung Alger' AND u.username='admin' LIMIT 1;
INSERT IGNORE INTO purchase_orders (number, supplier_id, order_date, status, subtotal, total, paid_amount, notes, user_id)
SELECT 'A-2026-000002', s.id, CURDATE()-1, 'BROUILLON', 11000, 11000, 0, 'Câbles/accessoires', u.id
FROM suppliers s CROSS JOIN users u WHERE s.name='Accessoires Pro' AND u.username='admin' LIMIT 1;
INSERT IGNORE INTO purchase_orders (number, supplier_id, order_date, status, subtotal, total, paid_amount, notes, user_id)
SELECT 'A-2026-000003', s.id, CURDATE()-2, 'RECU', 28000, 28000, 28000, 'Note 13 reçue', u.id
FROM suppliers s CROSS JOIN users u WHERE s.name='Xiaomi Distribution DZ' AND u.username='admin' LIMIT 1;
