-- ============================================================================
-- TechShop Manager - V007__remove_purchases_module.sql
-- Retire le module « Achats & Fournisseurs » (supprimé de l'application) :
--   * lignes de la matrice de permissions
--   * paramètre de numérotation des bons de commande
-- Les tables fournisseurs historiques restent en base (aucune donnée détruite).
-- ============================================================================

DELETE FROM permissions WHERE module = 'purchases';

DELETE FROM settings WHERE cle = 'numbering.purchase.prefix';
