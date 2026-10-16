-- ============================================================================
-- TechShop Manager - V005__phase4.sql
-- Phase 4 (caisse, point de vente, ventes) :
--   * table des encaissements de dette client (customer_payments) manquante au
--     schéma initial alors que le CustomerPaymentDao la référence ;
--   * données de démonstration (session de caisse laissée fermée, l'ouverture
--     se fait depuis l'application).
-- Les scripts sont découpés par instruction « ; » : aucun point-virgule dans
-- les libellés.
-- ============================================================================

-- -------------------------------------------- Encaissements dette client ---
CREATE TABLE IF NOT EXISTS customer_payments (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    customer_id     INT           NOT NULL,
    amount          DECIMAL(12,2) NOT NULL,
    payment_method  VARCHAR(20)   NOT NULL DEFAULT 'ESPECES' COMMENT 'ESPECES, CARTE, VIREMENT',
    payment_date    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cash_session_id INT           NULL,
    note            VARCHAR(255)  NULL,
    user_id         INT           NOT NULL,
    KEY idx_customer_payments_customer (customer_id),
    KEY idx_customer_payments_date (payment_date),
    CONSTRAINT fk_customer_payments_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_customer_payments_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id),
    CONSTRAINT fk_customer_payments_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
