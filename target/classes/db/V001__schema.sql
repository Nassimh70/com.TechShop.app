-- ============================================================================
-- TechShop Manager - V001__schema.sql
-- Schéma initial complet (SPEC §5) : tables, clés étrangères, index,
-- contraintes. Compatible MySQL 5.7+/MariaDB (XAMPP) : aucune fonctionnalité
-- exclusive à MySQL 8.
-- Conventions :
--   * moteur InnoDB, utf8mb4_unicode_ci
--   * suppressions logiques via la colonne « active » (produits, clients,
--     utilisateurs, fournisseurs, catégories)
--   * numérotation séquentielle par type et par année : V-2026-000001,
--     R-2026-000001 (réparation), A-2026-000001 (achat), C-2026-000001 (caisse)
--   * aucun point-virgule dans les libellés de données (les scripts sont
--     découpés par instruction SQL par MigrationService)
-- ============================================================================

-- ----------------------------------------------------------------- Rôles ---
CREATE TABLE roles (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL,
    label       VARCHAR(60)  NOT NULL,
    description VARCHAR(255) NULL,
    UNIQUE KEY uk_roles_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------- Permissions ---
-- Matrice par rôle et par module (SPEC §3 : vérifiées dans les services).
CREATE TABLE permissions (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    role_id      INT         NOT NULL,
    module       VARCHAR(60) NOT NULL,
    can_view     TINYINT(1)  NOT NULL DEFAULT 0,
    can_create   TINYINT(1)  NOT NULL DEFAULT 0,
    can_edit     TINYINT(1)  NOT NULL DEFAULT 0,
    can_delete   TINYINT(1)  NOT NULL DEFAULT 0,
    can_validate TINYINT(1)  NOT NULL DEFAULT 0,
    UNIQUE KEY uk_permissions_role_module (role_id, module),
    KEY idx_permissions_module (module),
    CONSTRAINT fk_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------ Utilisateurs --
CREATE TABLE users (
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    username            VARCHAR(60)  NOT NULL,
    password_hash       VARCHAR(100) NOT NULL COMMENT 'BCrypt (jBCrypt)',
    full_name           VARCHAR(120) NOT NULL,
    phone               VARCHAR(30)  NULL,
    email               VARCHAR(120) NULL,
    role_id             INT          NOT NULL,
    active              TINYINT(1)   NOT NULL DEFAULT 1,
    must_change_password TINYINT(1)  NOT NULL DEFAULT 0 COMMENT 'Changement obligatoire (admin initial)',
    failed_attempts     INT          NOT NULL DEFAULT 0 COMMENT 'Blocage après 5 echecs',
    locked_until        DATETIME     NULL COMMENT 'Fin du blocage temporaire',
    last_login          DATETIME     NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_users_username (username),
    KEY idx_users_role (role_id),
    KEY idx_users_active (active),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------- Journal d'activité --
CREATE TABLE activity_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT          NULL,
    action     VARCHAR(60)  NOT NULL COMMENT 'CONNEXION, CREATION, MODIFICATION, SUPPRESSION, REINIT_MDP...',
    entity     VARCHAR(60)  NULL COMMENT 'Table concernee',
    entity_id  INT          NULL,
    details    TEXT         NULL,
    ip         VARCHAR(45)  NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_activity_created (created_at),
    KEY idx_activity_user (user_id),
    KEY idx_activity_action (action),
    KEY idx_activity_entity (entity, entity_id),
    CONSTRAINT fk_activity_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------ Paramètres ---
CREATE TABLE settings (
    id     INT AUTO_INCREMENT PRIMARY KEY,
    cle    VARCHAR(80) NOT NULL COMMENT 'Ex. shop.name, sale.vat_default, ticket.footer',
    valeur TEXT        NULL,
    groupe VARCHAR(40) NULL COMMENT 'magasin, ventes, impression, stock, apparence',
    type   VARCHAR(20) NULL COMMENT 'STRING, INT, DECIMAL, BOOLEAN',
    UNIQUE KEY uk_settings_cle (cle)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------- Catégories et marques ---
CREATE TABLE categories (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(80) NOT NULL,
    parent_id INT         NULL,
    active    TINYINT(1)  NOT NULL DEFAULT 1,
    UNIQUE KEY uk_categories_name (name),
    KEY idx_categories_parent (parent_id),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE brands (
    id     INT AUTO_INCREMENT PRIMARY KEY,
    name   VARCHAR(80) NOT NULL,
    active TINYINT(1)  NOT NULL DEFAULT 1,
    UNIQUE KEY uk_brands_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------------- Produits ---
CREATE TABLE products (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    ref             VARCHAR(30)   NOT NULL COMMENT 'Reference interne, ex. P-000101',
    barcode         VARCHAR(60)   NULL,
    name            VARCHAR(180)  NOT NULL,
    description     TEXT          NULL,
    category_id     INT           NULL,
    brand_id        INT           NULL,
    unit            VARCHAR(20)   NOT NULL DEFAULT 'piece',
    purchase_price  DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    avg_cost        DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Prix d achat moyen pondere',
    sale_price      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    vat_rate        DECIMAL(5,2)  NOT NULL DEFAULT 19.00,
    alert_threshold INT           NOT NULL DEFAULT 5,
    quantity        INT           NOT NULL DEFAULT 0 COMMENT 'Cache du stock disponible',
    track_imei      TINYINT(1)    NOT NULL DEFAULT 0,
    warranty_months INT           NOT NULL DEFAULT 0,
    image_path      VARCHAR(255)  NULL,
    active          TINYINT(1)    NOT NULL DEFAULT 1,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_products_ref (ref),
    UNIQUE KEY uk_products_barcode (barcode),
    KEY idx_products_name (name),
    KEY idx_products_category (category_id),
    KEY idx_products_brand (brand_id),
    KEY idx_products_active (active),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------ Fournisseurs --
CREATE TABLE suppliers (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(140)  NOT NULL,
    contact_name VARCHAR(120)  NULL,
    phone        VARCHAR(30)   NULL,
    email        VARCHAR(120)  NULL,
    address      VARCHAR(255)  NULL,
    balance      DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Solde du au fournisseur',
    notes        TEXT          NULL,
    active       TINYINT(1)    NOT NULL DEFAULT 1,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_suppliers_name (name),
    KEY idx_suppliers_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------- Catégories de dépenses --
CREATE TABLE expense_categories (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(80) NOT NULL,
    description VARCHAR(255) NULL,
    active      TINYINT(1)  NOT NULL DEFAULT 1,
    UNIQUE KEY uk_expense_categories_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- Clients --
CREATE TABLE customers (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    code       VARCHAR(30)   NOT NULL COMMENT 'Ex. C-000001',
    full_name  VARCHAR(140)  NOT NULL,
    phone      VARCHAR(30)   NULL,
    phone2     VARCHAR(30)   NULL,
    email      VARCHAR(120)  NULL,
    address    VARCHAR(255)  NULL,
    balance    DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Dette du client',
    notes      TEXT          NULL,
    active     TINYINT(1)    NOT NULL DEFAULT 1,
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_customers_code (code),
    KEY idx_customers_name (full_name),
    KEY idx_customers_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------- Sessions de caisse -----
CREATE TABLE cash_sessions (
    id                 INT AUTO_INCREMENT PRIMARY KEY,
    number             VARCHAR(30)   NOT NULL COMMENT 'Ex. C-2026-000001',
    user_id            INT           NOT NULL,
    opened_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at          DATETIME      NULL,
    opening_float      DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Fonds initial',
    expected_cash      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    counted_cash       DECIMAL(12,2) NULL,
    difference         DECIMAL(12,2) NULL COMMENT 'Ecart compte - attendu',
    expected_card      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    expected_transfer  DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_sales        DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    status             VARCHAR(20)   NOT NULL DEFAULT 'OUVERTE' COMMENT 'OUVERTE, FERMEE',
    note               TEXT          NULL,
    UNIQUE KEY uk_cash_sessions_number (number),
    KEY idx_cash_sessions_status (status),
    KEY idx_cash_sessions_user (user_id, opened_at),
    CONSTRAINT fk_cash_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cash_movements (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    cash_session_id INT           NOT NULL,
    type            VARCHAR(20)   NOT NULL COMMENT 'ENTREE, SORTIE',
    amount          DECIMAL(12,2) NOT NULL,
    reason          VARCHAR(255)  NULL,
    category_id     INT           NULL COMMENT 'Catégorie de dépense',
    reference_type  VARCHAR(40)   NULL COMMENT 'SALE, REPAIR_PAYMENT, SUPPLIER_PAYMENT, CUSTOMER_PAYMENT...',
    reference_id    INT           NULL,
    user_id         INT           NOT NULL,
    movement_date   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note            TEXT          NULL,
    KEY idx_cash_movements_session (cash_session_id, movement_date),
    KEY idx_cash_movements_ref (reference_type, reference_id),
    KEY idx_cash_movements_category (category_id),
    CONSTRAINT fk_cash_movements_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id),
    CONSTRAINT fk_cash_movements_category FOREIGN KEY (category_id) REFERENCES expense_categories (id),
    CONSTRAINT fk_cash_movements_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------ Bons de commande --
CREATE TABLE purchase_orders (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    number        VARCHAR(30)   NOT NULL COMMENT 'Ex. A-2026-000001',
    supplier_id   INT           NOT NULL,
    order_date    DATE          NOT NULL,
    expected_date DATE          NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'BROUILLON' COMMENT 'BROUILLON, ENVOYE, RECU_PARTIEL, RECU, ANNULE',
    subtotal      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    discount      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    tax           DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total         DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    paid_amount   DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    notes         TEXT          NULL,
    user_id       INT           NOT NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_purchase_orders_number (number),
    KEY idx_purchase_orders_supplier (supplier_id),
    KEY idx_purchase_orders_status (status),
    KEY idx_purchase_orders_date (order_date),
    CONSTRAINT fk_purchase_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_purchase_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE purchase_order_items (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    order_id          INT           NOT NULL,
    product_id        INT           NOT NULL,
    quantity          INT           NOT NULL DEFAULT 1,
    quantity_received INT           NOT NULL DEFAULT 0,
    unit_cost         DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    discount          DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total             DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    KEY idx_po_items_order (order_id),
    KEY idx_po_items_product (product_id),
    CONSTRAINT fk_po_items_order FOREIGN KEY (order_id) REFERENCES purchase_orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_po_items_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------- Réceptions de marchandise
CREATE TABLE goods_receipts (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    number       VARCHAR(30)   NOT NULL COMMENT 'Ex. B-2026-000001',
    order_id     INT           NULL,
    supplier_id  INT           NOT NULL,
    receipt_date DATE          NOT NULL,
    total        DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    notes        TEXT          NULL,
    user_id      INT           NOT NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_goods_receipts_number (number),
    KEY idx_goods_receipts_supplier (supplier_id),
    KEY idx_goods_receipts_order (order_id),
    KEY idx_goods_receipts_date (receipt_date),
    CONSTRAINT fk_goods_receipts_order FOREIGN KEY (order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_goods_receipts_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_goods_receipts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------- Paiements fournisseurs
CREATE TABLE supplier_payments (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    number          VARCHAR(30)   NOT NULL COMMENT 'Ex. PF-2026-000001',
    supplier_id     INT           NOT NULL,
    amount          DECIMAL(12,2) NOT NULL,
    payment_method  VARCHAR(20)   NOT NULL DEFAULT 'ESPECES' COMMENT 'ESPECES, VIREMENT, CHEQUE',
    payment_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cash_session_id INT           NULL,
    note            TEXT          NULL,
    user_id         INT           NOT NULL,
    UNIQUE KEY uk_supplier_payments_number (number),
    KEY idx_supplier_payments_supplier (supplier_id),
    KEY idx_supplier_payments_date (payment_date),
    CONSTRAINT fk_supplier_payments_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_supplier_payments_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id),
    CONSTRAINT fk_supplier_payments_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------- Ventes --
CREATE TABLE sales (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    number          VARCHAR(30)   NOT NULL COMMENT 'Ex. V-2026-000001',
    sale_date       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    customer_id     INT           NULL,
    user_id         INT           NOT NULL COMMENT 'Vendeur',
    cash_session_id INT           NULL,
    subtotal        DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    discount        DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Remise globale',
    tax             DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total           DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    paid_amount     DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    due_amount      DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Dette generee',
    profit          DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Benefice (cout au moment de la vente)',
    payment_status  VARCHAR(20)   NOT NULL DEFAULT 'PAYEE' COMMENT 'PAYEE, PARTIELLE, IMPAYEE, REMBOURSEE',
    status          VARCHAR(20)   NOT NULL DEFAULT 'VALIDEE' COMMENT 'VALIDEE, ANNULEE',
    note            TEXT          NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sales_number (number),
    KEY idx_sales_date (sale_date),
    KEY idx_sales_customer (customer_id),
    KEY idx_sales_user (user_id),
    KEY idx_sales_status (payment_status),
    KEY idx_sales_session (cash_session_id),
    CONSTRAINT fk_sales_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_sales_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_sales_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sale_items (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    sale_id         INT           NOT NULL,
    product_id      INT           NOT NULL,
    product_unit_id INT           NULL COMMENT 'IMEI precis pour les produits suivis',
    description     VARCHAR(255)  NULL,
    quantity        INT           NOT NULL DEFAULT 1,
    unit_price      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    unit_cost       DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Cout fige a la vente',
    discount        DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Remise par ligne',
    vat_rate        DECIMAL(5,2)  NOT NULL DEFAULT 19.00,
    total           DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    warranty_months INT           NOT NULL DEFAULT 0,
    KEY idx_sale_items_sale (sale_id),
    KEY idx_sale_items_product (product_id),
    CONSTRAINT fk_sale_items_sale FOREIGN KEY (sale_id) REFERENCES sales (id) ON DELETE CASCADE,
    CONSTRAINT fk_sale_items_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------- Unités suivies (IMEI / n° de série) --
CREATE TABLE product_units (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    product_id       INT           NOT NULL,
    imei             VARCHAR(40)   NOT NULL,
    serial_number    VARCHAR(60)   NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'EN_STOCK' COMMENT 'EN_STOCK, VENDU, EN_REPARATION, RETOURNE, PERDU',
    purchase_price   DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    supplier_id      INT           NULL,
    sale_id          INT           NULL,
    sale_item_id     INT           NULL,
    repair_ticket_id INT           NULL,
    received_at      DATETIME      NULL,
    sold_at          DATETIME      NULL,
    UNIQUE KEY uk_product_units_imei (imei),
    KEY idx_product_units_product (product_id),
    KEY idx_product_units_status (status),
    KEY idx_product_units_sale (sale_id),
    CONSTRAINT fk_product_units_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_units_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_product_units_sale FOREIGN KEY (sale_id) REFERENCES sales (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Lien différé (sale_items -> product_units) pour éviter les dépendances circulaires
ALTER TABLE sale_items
    ADD CONSTRAINT fk_sale_items_unit FOREIGN KEY (product_unit_id) REFERENCES product_units (id);

-- -------------------------------------------------------- Paiements ventes --
CREATE TABLE sale_payments (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    sale_id         INT           NOT NULL,
    payment_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    method          VARCHAR(20)   NOT NULL COMMENT 'ESPECES, CARTE, VIREMENT, CREDIT',
    amount          DECIMAL(12,2) NOT NULL,
    cash_session_id INT           NULL,
    note            VARCHAR(255)  NULL,
    user_id         INT           NOT NULL,
    KEY idx_sale_payments_sale (sale_id),
    KEY idx_sale_payments_date (payment_date),
    KEY idx_sale_payments_session (cash_session_id),
    CONSTRAINT fk_sale_payments_sale FOREIGN KEY (sale_id) REFERENCES sales (id) ON DELETE CASCADE,
    CONSTRAINT fk_sale_payments_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id),
    CONSTRAINT fk_sale_payments_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------- Retours / remboursements -
CREATE TABLE sale_returns (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    number          VARCHAR(30)   NOT NULL COMMENT 'Ex. RT-2026-000001',
    sale_id         INT           NOT NULL,
    return_date     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total           DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    reason          VARCHAR(255)  NULL,
    refund_method   VARCHAR(20)   NULL COMMENT 'ESPECES, CARTE, VIREMENT, AVOIR',
    cash_session_id INT           NULL,
    user_id         INT           NOT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sale_returns_number (number),
    KEY idx_sale_returns_sale (sale_id),
    KEY idx_sale_returns_date (return_date),
    CONSTRAINT fk_sale_returns_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_sale_returns_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id),
    CONSTRAINT fk_sale_returns_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sale_return_items (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    return_id    INT NOT NULL,
    sale_item_id INT NOT NULL,
    quantity     INT NOT NULL DEFAULT 1,
    unit_price   DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total        DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    KEY idx_sale_return_items_return (return_id),
    CONSTRAINT fk_sale_return_items_return FOREIGN KEY (return_id) REFERENCES sale_returns (id) ON DELETE CASCADE,
    CONSTRAINT fk_sale_return_items_item FOREIGN KEY (sale_item_id) REFERENCES sale_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------- Mouvements de stock ---
CREATE TABLE stock_movements (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id       INT           NOT NULL,
    product_unit_id  INT           NULL,
    type             VARCHAR(20)   NOT NULL COMMENT 'ENTREE, SORTIE, AJUSTEMENT, PERTE, RETOUR, INVENTAIRE',
    quantity         INT           NOT NULL COMMENT 'Signe : + entree, - sortie',
    quantity_before  INT           NOT NULL DEFAULT 0,
    quantity_after   INT           NOT NULL DEFAULT 0,
    unit_cost        DECIMAL(12,2) NULL,
    reason           VARCHAR(255)  NULL,
    reference_type   VARCHAR(40)   NULL COMMENT 'SALE, GOODS_RECEIPT, REPAIR, INVENTORY, MANUAL...',
    reference_id     INT           NULL,
    user_id          INT           NOT NULL,
    movement_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_stock_movements_product (product_id, movement_date),
    KEY idx_stock_movements_type (type),
    KEY idx_stock_movements_date (movement_date),
    KEY idx_stock_movements_ref (reference_type, reference_id),
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_stock_movements_unit FOREIGN KEY (product_unit_id) REFERENCES product_units (id),
    CONSTRAINT fk_stock_movements_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------- SAV / Réparations ------
CREATE TABLE repair_tickets (
    id                 INT AUTO_INCREMENT PRIMARY KEY,
    number             VARCHAR(30)   NOT NULL COMMENT 'Ex. R-2026-000001',
    customer_id        INT           NOT NULL,
    device_type        VARCHAR(60)   NULL COMMENT 'Telephone, tablette, PC portable...',
    brand              VARCHAR(60)   NULL,
    model              VARCHAR(120)  NULL,
    serial_or_imei     VARCHAR(60)   NULL,
    device_password    VARCHAR(60)   NULL COMMENT 'Code de deverrouillage communique par le client',
    accessories        VARCHAR(255)  NULL,
    physical_condition TEXT          NULL COMMENT 'Etat a la reception',
    reported_issue     TEXT          NULL COMMENT 'Panne declaree',
    diagnosis          TEXT          NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'RECU' COMMENT 'RECU, DIAGNOSTIC, ATTENTE_PIECE, EN_REPARATION, PRETE, LIVREE, ANNULEE',
    technician_id      INT           NULL,
    received_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    promised_date      DATE          NULL,
    completed_at       DATETIME      NULL,
    labor_cost         DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Main d oeuvre',
    parts_total        DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    discount           DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total              DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    deposit_amount     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Acompte',
    paid_amount        DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    due_amount         DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    warranty_days      INT           NOT NULL DEFAULT 0 COMMENT 'Garantie reparation',
    internal_notes     TEXT          NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_repair_tickets_number (number),
    KEY idx_repair_tickets_customer (customer_id),
    KEY idx_repair_tickets_status (status),
    KEY idx_repair_tickets_received (received_at),
    KEY idx_repair_tickets_tech (technician_id),
    KEY idx_repair_tickets_imei (serial_or_imei),
    CONSTRAINT fk_repair_tickets_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_repair_tickets_tech FOREIGN KEY (technician_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Lien différé : une unité (IMEI) peut être immobilisée par un dossier SAV
ALTER TABLE product_units
    ADD CONSTRAINT fk_product_units_repair FOREIGN KEY (repair_ticket_id) REFERENCES repair_tickets (id);

CREATE TABLE repair_status_history (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    repair_id  INT         NOT NULL,
    old_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NOT NULL,
    comment    VARCHAR(255) NULL,
    user_id    INT         NOT NULL,
    changed_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_repair_history_repair (repair_id, changed_at),
    CONSTRAINT fk_repair_history_repair FOREIGN KEY (repair_id) REFERENCES repair_tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_repair_history_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE repair_parts (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    repair_id         INT           NOT NULL,
    product_id        INT           NOT NULL,
    product_unit_id   INT           NULL,
    quantity          INT           NOT NULL DEFAULT 1,
    unit_cost         DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    unit_price        DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Prix facture au client',
    total             DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    stock_movement_id BIGINT        NULL,
    added_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id           INT           NOT NULL,
    KEY idx_repair_parts_repair (repair_id),
    KEY idx_repair_parts_product (product_id),
    CONSTRAINT fk_repair_parts_repair FOREIGN KEY (repair_id) REFERENCES repair_tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_repair_parts_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_repair_parts_unit FOREIGN KEY (product_unit_id) REFERENCES product_units (id),
    CONSTRAINT fk_repair_parts_movement FOREIGN KEY (stock_movement_id) REFERENCES stock_movements (id),
    CONSTRAINT fk_repair_parts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE repair_payments (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    repair_id       INT           NOT NULL,
    payment_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    method          VARCHAR(20)   NOT NULL DEFAULT 'ESPECES' COMMENT 'ESPECES, CARTE, VIREMENT, CREDIT',
    amount          DECIMAL(12,2) NOT NULL,
    cash_session_id INT           NULL,
    note            VARCHAR(255)  NULL,
    user_id         INT           NOT NULL,
    KEY idx_repair_payments_repair (repair_id),
    KEY idx_repair_payments_date (payment_date),
    KEY idx_repair_payments_session (cash_session_id),
    CONSTRAINT fk_repair_payments_repair FOREIGN KEY (repair_id) REFERENCES repair_tickets (id),
    CONSTRAINT fk_repair_payments_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id),
    CONSTRAINT fk_repair_payments_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE repair_attachments (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    repair_id   INT          NOT NULL,
    file_name   VARCHAR(180) NOT NULL,
    file_path   VARCHAR(255) NOT NULL,
    mime_type   VARCHAR(80)  NULL,
    size_bytes  BIGINT       NULL,
    uploaded_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id     INT          NOT NULL,
    KEY idx_repair_attachments_repair (repair_id),
    CONSTRAINT fk_repair_attachments_repair FOREIGN KEY (repair_id) REFERENCES repair_tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_repair_attachments_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------- Journal des notifications clients ---
CREATE TABLE notifications_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel     VARCHAR(20)  NOT NULL COMMENT 'SMS, APPEL, EMAIL, INTERNE',
    customer_id INT          NULL,
    repair_id   INT          NULL,
    sale_id     INT          NULL,
    recipient   VARCHAR(140) NULL,
    message     TEXT         NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'SIMULE' COMMENT 'SIMULE, ENVOYE, ECHEC',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id     INT          NULL,
    KEY idx_notifications_created (created_at),
    KEY idx_notifications_customer (customer_id),
    CONSTRAINT fk_notifications_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_notifications_repair FOREIGN KEY (repair_id) REFERENCES repair_tickets (id),
    CONSTRAINT fk_notifications_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
