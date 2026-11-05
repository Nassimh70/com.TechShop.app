-- ============================================================================
-- TechShop Manager - SQLite schema (V001)
-- Schéma final mono-rôle (après retrait des tables roles / permissions).
-- Types SQLite : INTEGER pour les identifiants et booléens, TEXT pour les
-- dates ISO et les chaînes, NUMERIC pour les montants.
-- Les clés étrangères sont activées sur chaque connexion par DatabaseManager.
-- ============================================================================

-- ------------------------------------------------------------ Utilisateurs --
CREATE TABLE users (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    username             TEXT    NOT NULL,
    password_hash        TEXT    NOT NULL,
    full_name            TEXT    NOT NULL,
    phone                TEXT,
    email                TEXT,
    active               INTEGER NOT NULL DEFAULT 1,
    must_change_password INTEGER NOT NULL DEFAULT 0,
    failed_attempts      INTEGER NOT NULL DEFAULT 0,
    locked_until         TEXT,
    last_login           TEXT,
    created_at           TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    updated_at           TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    CONSTRAINT uk_users_username UNIQUE (username)
);
CREATE INDEX idx_users_active ON users (active);

-- ------------------------------------------------------- Journal d'activité --
CREATE TABLE activity_log (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER,
    action     TEXT    NOT NULL,
    entity     TEXT,
    entity_id  INTEGER,
    details    TEXT,
    ip         TEXT,
    created_at TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    CONSTRAINT fk_activity_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_activity_created ON activity_log (created_at);
CREATE INDEX idx_activity_user ON activity_log (user_id);
CREATE INDEX idx_activity_action ON activity_log (action);
CREATE INDEX idx_activity_entity ON activity_log (entity, entity_id);

-- ------------------------------------------------------------ Paramètres ---
CREATE TABLE settings (
    id     INTEGER PRIMARY KEY AUTOINCREMENT,
    cle    TEXT NOT NULL,
    valeur TEXT,
    groupe TEXT,
    type   TEXT,
    CONSTRAINT uk_settings_cle UNIQUE (cle)
);

-- -------------------------------------------------- Catégories et marques ---
CREATE TABLE categories (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    name      TEXT    NOT NULL,
    parent_id INTEGER,
    active    INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT uk_categories_name UNIQUE (name),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
);
CREATE INDEX idx_categories_parent ON categories (parent_id);

CREATE TABLE brands (
    id     INTEGER PRIMARY KEY AUTOINCREMENT,
    name   TEXT    NOT NULL,
    active INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT uk_brands_name UNIQUE (name)
);

-- -------------------------------------------------------------- Produits ---
CREATE TABLE products (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    ref             TEXT    NOT NULL,
    barcode         TEXT,
    name            TEXT    NOT NULL,
    description     TEXT,
    category_id     INTEGER,
    brand_id        INTEGER,
    unit            TEXT    NOT NULL DEFAULT 'pièce',
    purchase_price  NUMERIC NOT NULL DEFAULT 0.00,
    avg_cost        NUMERIC NOT NULL DEFAULT 0.00,
    sale_price      NUMERIC NOT NULL DEFAULT 0.00,
    vat_rate        NUMERIC NOT NULL DEFAULT 19.00,
    alert_threshold INTEGER NOT NULL DEFAULT 5,
    quantity        INTEGER NOT NULL DEFAULT 0,
    track_imei      INTEGER NOT NULL DEFAULT 0,
    warranty_months INTEGER NOT NULL DEFAULT 0,
    image_path      TEXT,
    active          INTEGER NOT NULL DEFAULT 1,
    created_at      TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    updated_at      TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    CONSTRAINT uk_products_ref UNIQUE (ref),
    CONSTRAINT uk_products_barcode UNIQUE (barcode),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands (id)
);
CREATE INDEX idx_products_name ON products (name);
CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_brand ON products (brand_id);
CREATE INDEX idx_products_active ON products (active);

-- ------------------------------------------------------------ Fournisseurs --
CREATE TABLE suppliers (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT    NOT NULL,
    contact_name TEXT,
    phone        TEXT,
    email        TEXT,
    address      TEXT,
    balance      NUMERIC NOT NULL DEFAULT 0.00,
    notes        TEXT,
    active       INTEGER NOT NULL DEFAULT 1,
    created_at   TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    CONSTRAINT uk_suppliers_name UNIQUE (name)
);
CREATE INDEX idx_suppliers_phone ON suppliers (phone);

-- ------------------------------------------------- Catégories de dépenses --
CREATE TABLE expense_categories (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL,
    description TEXT,
    active      INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT uk_expense_categories_name UNIQUE (name)
);

-- ---------------------------------------------------------------- Clients --
CREATE TABLE customers (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    code       TEXT    NOT NULL,
    full_name  TEXT    NOT NULL,
    phone      TEXT,
    phone2     TEXT,
    email      TEXT,
    address    TEXT,
    balance    NUMERIC NOT NULL DEFAULT 0.00,
    notes      TEXT,
    active     INTEGER NOT NULL DEFAULT 1,
    created_at TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    updated_at TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    CONSTRAINT uk_customers_code UNIQUE (code)
);
CREATE INDEX idx_customers_name ON customers (full_name);
CREATE INDEX idx_customers_phone ON customers (phone);

-- ------------------------------------------------- Sessions de caisse -----
CREATE TABLE cash_sessions (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    number            TEXT    NOT NULL,
    user_id           INTEGER NOT NULL,
    opened_at         TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    closed_at         TEXT,
    opening_float     NUMERIC NOT NULL DEFAULT 0.00,
    expected_cash     NUMERIC NOT NULL DEFAULT 0.00,
    counted_cash      NUMERIC,
    difference        NUMERIC,
    expected_card     NUMERIC NOT NULL DEFAULT 0.00,
    expected_transfer NUMERIC NOT NULL DEFAULT 0.00,
    total_sales       NUMERIC NOT NULL DEFAULT 0.00,
    status            TEXT    NOT NULL DEFAULT 'OUVERTE',
    note              TEXT,
    CONSTRAINT uk_cash_sessions_number UNIQUE (number),
    CONSTRAINT fk_cash_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_cash_sessions_status ON cash_sessions (status);
CREATE INDEX idx_cash_sessions_user ON cash_sessions (user_id, opened_at);

CREATE TABLE cash_movements (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    cash_session_id INTEGER NOT NULL,
    type            TEXT    NOT NULL,
    amount          NUMERIC NOT NULL,
    reason          TEXT,
    category_id     INTEGER,
    reference_type  TEXT,
    reference_id    INTEGER,
    user_id         INTEGER NOT NULL,
    movement_date   TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    note            TEXT,
    CONSTRAINT fk_cash_movements_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id),
    CONSTRAINT fk_cash_movements_category FOREIGN KEY (category_id) REFERENCES expense_categories (id),
    CONSTRAINT fk_cash_movements_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_cash_movements_session ON cash_movements (cash_session_id, movement_date);
CREATE INDEX idx_cash_movements_ref ON cash_movements (reference_type, reference_id);
CREATE INDEX idx_cash_movements_category ON cash_movements (category_id);

-- ------------------------------------------------------ Bons de commande --
CREATE TABLE purchase_orders (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    number        TEXT    NOT NULL,
    supplier_id   INTEGER NOT NULL,
    order_date    TEXT    NOT NULL,
    expected_date TEXT,
    status        TEXT    NOT NULL DEFAULT 'BROUILLON',
    subtotal      NUMERIC NOT NULL DEFAULT 0.00,
    discount      NUMERIC NOT NULL DEFAULT 0.00,
    tax           NUMERIC NOT NULL DEFAULT 0.00,
    total         NUMERIC NOT NULL DEFAULT 0.00,
    paid_amount   NUMERIC NOT NULL DEFAULT 0.00,
    notes         TEXT,
    user_id       INTEGER NOT NULL,
    created_at    TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    CONSTRAINT uk_purchase_orders_number UNIQUE (number),
    CONSTRAINT fk_purchase_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_purchase_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_purchase_orders_supplier ON purchase_orders (supplier_id);
CREATE INDEX idx_purchase_orders_status ON purchase_orders (status);
CREATE INDEX idx_purchase_orders_date ON purchase_orders (order_date);

CREATE TABLE purchase_order_items (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id          INTEGER NOT NULL,
    product_id        INTEGER NOT NULL,
    quantity          INTEGER NOT NULL DEFAULT 1,
    quantity_received INTEGER NOT NULL DEFAULT 0,
    unit_cost         NUMERIC NOT NULL DEFAULT 0.00,
    discount          NUMERIC NOT NULL DEFAULT 0.00,
    total             NUMERIC NOT NULL DEFAULT 0.00,
    CONSTRAINT fk_po_items_order FOREIGN KEY (order_id) REFERENCES purchase_orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_po_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);
CREATE INDEX idx_po_items_order ON purchase_order_items (order_id);
CREATE INDEX idx_po_items_product ON purchase_order_items (product_id);

-- ------------------------------------------------- Réceptions de marchandise
CREATE TABLE goods_receipts (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    number       TEXT    NOT NULL,
    order_id     INTEGER,
    supplier_id  INTEGER NOT NULL,
    receipt_date TEXT    NOT NULL,
    total        NUMERIC NOT NULL DEFAULT 0.00,
    notes        TEXT,
    user_id      INTEGER NOT NULL,
    created_at   TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    CONSTRAINT uk_goods_receipts_number UNIQUE (number),
    CONSTRAINT fk_goods_receipts_order FOREIGN KEY (order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_goods_receipts_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_goods_receipts_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_goods_receipts_supplier ON goods_receipts (supplier_id);
CREATE INDEX idx_goods_receipts_order ON goods_receipts (order_id);
CREATE INDEX idx_goods_receipts_date ON goods_receipts (receipt_date);

-- Table référencée par GoodsReceiptDao et absente du schéma MySQL initial.
CREATE TABLE goods_receipt_items (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    receipt_id      INTEGER NOT NULL,
    product_id      INTEGER NOT NULL,
    product_unit_id INTEGER,
    imei            TEXT,
    quantity        INTEGER NOT NULL DEFAULT 1,
    unit_cost       NUMERIC NOT NULL DEFAULT 0.00,
    total           NUMERIC NOT NULL DEFAULT 0.00,
    CONSTRAINT fk_gri_receipt FOREIGN KEY (receipt_id) REFERENCES goods_receipts (id) ON DELETE CASCADE,
    CONSTRAINT fk_gri_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_gri_unit FOREIGN KEY (product_unit_id) REFERENCES product_units (id)
);
CREATE INDEX idx_goods_receipt_items_receipt ON goods_receipt_items (receipt_id);
CREATE INDEX idx_goods_receipt_items_product ON goods_receipt_items (product_id);

-- --------------------------------------------------- Paiements fournisseurs
CREATE TABLE supplier_payments (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    number          TEXT    NOT NULL,
    supplier_id     INTEGER NOT NULL,
    amount          NUMERIC NOT NULL,
    payment_method  TEXT    NOT NULL DEFAULT 'ESPECES',
    payment_date    TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    cash_session_id INTEGER,
    note            TEXT,
    user_id         INTEGER NOT NULL,
    CONSTRAINT uk_supplier_payments_number UNIQUE (number),
    CONSTRAINT fk_supplier_payments_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_supplier_payments_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id),
    CONSTRAINT fk_supplier_payments_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_supplier_payments_supplier ON supplier_payments (supplier_id);
CREATE INDEX idx_supplier_payments_date ON supplier_payments (payment_date);

-- ----------------------------------------------------------------- Ventes --
CREATE TABLE sales (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    number          TEXT    NOT NULL,
    sale_date       TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    customer_id     INTEGER,
    user_id         INTEGER NOT NULL,
    cash_session_id INTEGER,
    subtotal        NUMERIC NOT NULL DEFAULT 0.00,
    discount        NUMERIC NOT NULL DEFAULT 0.00,
    tax             NUMERIC NOT NULL DEFAULT 0.00,
    total           NUMERIC NOT NULL DEFAULT 0.00,
    paid_amount     NUMERIC NOT NULL DEFAULT 0.00,
    due_amount      NUMERIC NOT NULL DEFAULT 0.00,
    profit          NUMERIC NOT NULL DEFAULT 0.00,
    payment_status  TEXT    NOT NULL DEFAULT 'PAYEE',
    status          TEXT    NOT NULL DEFAULT 'VALIDEE',
    note            TEXT,
    created_at      TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    CONSTRAINT uk_sales_number UNIQUE (number),
    CONSTRAINT fk_sales_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_sales_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_sales_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id)
);
CREATE INDEX idx_sales_date ON sales (sale_date);
CREATE INDEX idx_sales_customer ON sales (customer_id);
CREATE INDEX idx_sales_user ON sales (user_id);
CREATE INDEX idx_sales_status ON sales (payment_status);
CREATE INDEX idx_sales_session ON sales (cash_session_id);

-- ------------------------------------- Unités suivies (IMEI / n° de série) --
CREATE TABLE product_units (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id       INTEGER NOT NULL,
    imei             TEXT    NOT NULL,
    serial_number    TEXT,
    status           TEXT    NOT NULL DEFAULT 'EN_STOCK',
    purchase_price   NUMERIC NOT NULL DEFAULT 0.00,
    supplier_id      INTEGER,
    sale_id          INTEGER,
    sale_item_id     INTEGER,
    repair_ticket_id INTEGER,
    received_at      TEXT,
    sold_at          TEXT,
    CONSTRAINT uk_product_units_imei UNIQUE (imei),
    CONSTRAINT fk_product_units_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_units_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_product_units_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_product_units_repair FOREIGN KEY (repair_ticket_id) REFERENCES repair_tickets (id)
);
CREATE INDEX idx_product_units_product ON product_units (product_id);
CREATE INDEX idx_product_units_status ON product_units (status);
CREATE INDEX idx_product_units_sale ON product_units (sale_id);

CREATE TABLE sale_items (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    sale_id         INTEGER NOT NULL,
    product_id      INTEGER NOT NULL,
    product_unit_id INTEGER,
    description     TEXT,
    quantity        INTEGER NOT NULL DEFAULT 1,
    unit_price      NUMERIC NOT NULL DEFAULT 0.00,
    unit_cost       NUMERIC NOT NULL DEFAULT 0.00,
    discount        NUMERIC NOT NULL DEFAULT 0.00,
    vat_rate        NUMERIC NOT NULL DEFAULT 19.00,
    total           NUMERIC NOT NULL DEFAULT 0.00,
    warranty_months INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_sale_items_sale FOREIGN KEY (sale_id) REFERENCES sales (id) ON DELETE CASCADE,
    CONSTRAINT fk_sale_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_sale_items_unit FOREIGN KEY (product_unit_id) REFERENCES product_units (id)
);
CREATE INDEX idx_sale_items_sale ON sale_items (sale_id);
CREATE INDEX idx_sale_items_product ON sale_items (product_id);

-- -------------------------------------------------------- Paiements ventes --
CREATE TABLE sale_payments (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    sale_id         INTEGER NOT NULL,
    payment_date    TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    method          TEXT    NOT NULL,
    amount          NUMERIC NOT NULL,
    cash_session_id INTEGER,
    note            TEXT,
    user_id         INTEGER NOT NULL,
    CONSTRAINT fk_sale_payments_sale FOREIGN KEY (sale_id) REFERENCES sales (id) ON DELETE CASCADE,
    CONSTRAINT fk_sale_payments_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id),
    CONSTRAINT fk_sale_payments_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_sale_payments_sale ON sale_payments (sale_id);
CREATE INDEX idx_sale_payments_date ON sale_payments (payment_date);
CREATE INDEX idx_sale_payments_session ON sale_payments (cash_session_id);

-- ------------------------------------------------- Retours / remboursements -
CREATE TABLE sale_returns (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    number          TEXT    NOT NULL,
    sale_id         INTEGER NOT NULL,
    return_date     TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    total           NUMERIC NOT NULL DEFAULT 0.00,
    reason          TEXT,
    refund_method   TEXT,
    cash_session_id INTEGER,
    user_id         INTEGER NOT NULL,
    created_at      TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    CONSTRAINT uk_sale_returns_number UNIQUE (number),
    CONSTRAINT fk_sale_returns_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_sale_returns_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id),
    CONSTRAINT fk_sale_returns_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_sale_returns_sale ON sale_returns (sale_id);
CREATE INDEX idx_sale_returns_date ON sale_returns (return_date);

CREATE TABLE sale_return_items (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    return_id    INTEGER NOT NULL,
    sale_item_id INTEGER NOT NULL,
    quantity     INTEGER NOT NULL DEFAULT 1,
    unit_price   NUMERIC NOT NULL DEFAULT 0.00,
    total        NUMERIC NOT NULL DEFAULT 0.00,
    CONSTRAINT fk_sale_return_items_return FOREIGN KEY (return_id) REFERENCES sale_returns (id) ON DELETE CASCADE,
    CONSTRAINT fk_sale_return_items_item FOREIGN KEY (sale_item_id) REFERENCES sale_items (id)
);
CREATE INDEX idx_sale_return_items_return ON sale_return_items (return_id);

-- --------------------------------------------------- Mouvements de stock ---
CREATE TABLE stock_movements (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id      INTEGER NOT NULL,
    product_unit_id INTEGER,
    type            TEXT    NOT NULL,
    quantity        INTEGER NOT NULL,
    quantity_before INTEGER NOT NULL DEFAULT 0,
    quantity_after  INTEGER NOT NULL DEFAULT 0,
    unit_cost       NUMERIC,
    reason          TEXT,
    reference_type  TEXT,
    reference_id    INTEGER,
    user_id         INTEGER NOT NULL,
    movement_date   TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_stock_movements_unit FOREIGN KEY (product_unit_id) REFERENCES product_units (id),
    CONSTRAINT fk_stock_movements_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_stock_movements_product ON stock_movements (product_id, movement_date);
CREATE INDEX idx_stock_movements_type ON stock_movements (type);
CREATE INDEX idx_stock_movements_date ON stock_movements (movement_date);
CREATE INDEX idx_stock_movements_ref ON stock_movements (reference_type, reference_id);

-- ------------------------------------------------- SAV / Réparations ------
CREATE TABLE repair_tickets (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    number             TEXT    NOT NULL,
    customer_id        INTEGER NOT NULL,
    device_type        TEXT,
    brand              TEXT,
    model              TEXT,
    serial_or_imei     TEXT,
    device_password    TEXT,
    accessories        TEXT,
    physical_condition TEXT,
    reported_issue     TEXT,
    diagnosis          TEXT,
    quoted_amount      NUMERIC NOT NULL DEFAULT 0.00,
    status             TEXT    NOT NULL DEFAULT 'RECU',
    technician_id      INTEGER,
    received_at        TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    promised_date      TEXT,
    completed_at       TEXT,
    labor_cost         NUMERIC NOT NULL DEFAULT 0.00,
    parts_total        NUMERIC NOT NULL DEFAULT 0.00,
    discount           NUMERIC NOT NULL DEFAULT 0.00,
    total              NUMERIC NOT NULL DEFAULT 0.00,
    deposit_amount     NUMERIC NOT NULL DEFAULT 0.00,
    paid_amount        NUMERIC NOT NULL DEFAULT 0.00,
    due_amount         NUMERIC NOT NULL DEFAULT 0.00,
    warranty_days      INTEGER NOT NULL DEFAULT 0,
    internal_notes     TEXT,
    created_at         TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    updated_at         TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    CONSTRAINT uk_repair_tickets_number UNIQUE (number),
    CONSTRAINT fk_repair_tickets_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_repair_tickets_tech FOREIGN KEY (technician_id) REFERENCES users (id)
);
CREATE INDEX idx_repair_tickets_customer ON repair_tickets (customer_id);
CREATE INDEX idx_repair_tickets_status ON repair_tickets (status);
CREATE INDEX idx_repair_tickets_received ON repair_tickets (received_at);
CREATE INDEX idx_repair_tickets_tech ON repair_tickets (technician_id);
CREATE INDEX idx_repair_tickets_imei ON repair_tickets (serial_or_imei);

CREATE TABLE repair_status_history (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    repair_id  INTEGER NOT NULL,
    old_status TEXT,
    new_status TEXT    NOT NULL,
    comment    TEXT,
    user_id    INTEGER NOT NULL,
    changed_at TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    CONSTRAINT fk_repair_history_repair FOREIGN KEY (repair_id) REFERENCES repair_tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_repair_history_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_repair_history_repair ON repair_status_history (repair_id, changed_at);

CREATE TABLE repair_parts (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    repair_id         INTEGER NOT NULL,
    product_id        INTEGER NOT NULL,
    product_unit_id   INTEGER,
    quantity          INTEGER NOT NULL DEFAULT 1,
    unit_cost         NUMERIC NOT NULL DEFAULT 0.00,
    unit_price        NUMERIC NOT NULL DEFAULT 0.00,
    total             NUMERIC NOT NULL DEFAULT 0.00,
    stock_movement_id INTEGER,
    added_at          TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    user_id           INTEGER NOT NULL,
    CONSTRAINT fk_repair_parts_repair FOREIGN KEY (repair_id) REFERENCES repair_tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_repair_parts_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_repair_parts_unit FOREIGN KEY (product_unit_id) REFERENCES product_units (id),
    CONSTRAINT fk_repair_parts_movement FOREIGN KEY (stock_movement_id) REFERENCES stock_movements (id),
    CONSTRAINT fk_repair_parts_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_repair_parts_repair ON repair_parts (repair_id);
CREATE INDEX idx_repair_parts_product ON repair_parts (product_id);

CREATE TABLE repair_payments (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    repair_id       INTEGER NOT NULL,
    payment_date    TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    method          TEXT    NOT NULL DEFAULT 'ESPECES',
    amount          NUMERIC NOT NULL,
    cash_session_id INTEGER,
    note            TEXT,
    user_id         INTEGER NOT NULL,
    CONSTRAINT fk_repair_payments_repair FOREIGN KEY (repair_id) REFERENCES repair_tickets (id),
    CONSTRAINT fk_repair_payments_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id),
    CONSTRAINT fk_repair_payments_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_repair_payments_repair ON repair_payments (repair_id);
CREATE INDEX idx_repair_payments_date ON repair_payments (payment_date);
CREATE INDEX idx_repair_payments_session ON repair_payments (cash_session_id);

CREATE TABLE repair_attachments (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    repair_id   INTEGER NOT NULL,
    file_name   TEXT    NOT NULL,
    file_path   TEXT    NOT NULL,
    mime_type   TEXT,
    size_bytes  INTEGER,
    uploaded_at TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    user_id     INTEGER NOT NULL,
    CONSTRAINT fk_repair_attachments_repair FOREIGN KEY (repair_id) REFERENCES repair_tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_repair_attachments_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_repair_attachments_repair ON repair_attachments (repair_id);

-- -------------------------------------------- Encaissements dette client ---
CREATE TABLE customer_payments (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id     INTEGER NOT NULL,
    amount          NUMERIC NOT NULL,
    payment_method  TEXT    NOT NULL DEFAULT 'ESPECES',
    payment_date    TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    cash_session_id INTEGER,
    note            TEXT,
    user_id         INTEGER NOT NULL,
    CONSTRAINT fk_customer_payments_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_customer_payments_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id),
    CONSTRAINT fk_customer_payments_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_customer_payments_customer ON customer_payments (customer_id);
CREATE INDEX idx_customer_payments_date ON customer_payments (payment_date);

-- ------------------------------------- Journal des notifications clients ---
CREATE TABLE notifications_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    channel     TEXT    NOT NULL,
    customer_id INTEGER,
    repair_id   INTEGER,
    sale_id     INTEGER,
    recipient   TEXT,
    message     TEXT,
    status      TEXT    NOT NULL DEFAULT 'SIMULE',
    created_at  TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
    user_id     INTEGER,
    CONSTRAINT fk_notifications_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_notifications_repair FOREIGN KEY (repair_id) REFERENCES repair_tickets (id),
    CONSTRAINT fk_notifications_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_notifications_created ON notifications_log (created_at);
CREATE INDEX idx_notifications_customer ON notifications_log (customer_id);

-- ------------------------------------------------- updated_at automatique --
CREATE TRIGGER trg_users_updated_at
AFTER UPDATE ON users
FOR EACH ROW
WHEN NEW.updated_at = OLD.updated_at
BEGIN
    UPDATE users SET updated_at = datetime('now', 'localtime') WHERE id = NEW.id;
END;

CREATE TRIGGER trg_products_updated_at
AFTER UPDATE ON products
FOR EACH ROW
WHEN NEW.updated_at = OLD.updated_at
BEGIN
    UPDATE products SET updated_at = datetime('now', 'localtime') WHERE id = NEW.id;
END;

CREATE TRIGGER trg_customers_updated_at
AFTER UPDATE ON customers
FOR EACH ROW
WHEN NEW.updated_at = OLD.updated_at
BEGIN
    UPDATE customers SET updated_at = datetime('now', 'localtime') WHERE id = NEW.id;
END;

CREATE TRIGGER trg_repair_tickets_updated_at
AFTER UPDATE ON repair_tickets
FOR EACH ROW
WHEN NEW.updated_at = OLD.updated_at
BEGIN
    UPDATE repair_tickets SET updated_at = datetime('now', 'localtime') WHERE id = NEW.id;
END;
