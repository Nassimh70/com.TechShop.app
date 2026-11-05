-- Suppression du module Utilisateurs : rôles et permissions.
-- Les comptes restent disponibles pour la connexion (table users conservée),
-- mais ils partagent désormais un profil unique « Gérant » codé dans
-- l'application. L'historique (user_id) des ventes, de la caisse et des
-- réparations est conservé.

-- 1. Supprimer la matrice de permissions (dépend des rôles).
DROP TABLE IF EXISTS permissions;

-- 2. Retirer la clé étrangère users.role_id -> roles.id si elle existe.
SET @fk_users_role := (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'role_id'
      AND REFERENCED_TABLE_NAME = 'roles'
    LIMIT 1
);
SET @sql_drop_fk := IF(@fk_users_role IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE users DROP FOREIGN KEY `', @fk_users_role, '`'));
PREPARE stmt_drop_fk FROM @sql_drop_fk;
EXECUTE stmt_drop_fk;
DEALLOCATE PREPARE stmt_drop_fk;

-- 3. Retirer la colonne role_id si elle existe encore.
SET @col_role_id := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'role_id'
);
SET @sql_drop_col := IF(@col_role_id = 0,
    'SELECT 1',
    'ALTER TABLE users DROP COLUMN role_id');
PREPARE stmt_drop_col FROM @sql_drop_col;
EXECUTE stmt_drop_col;
DEALLOCATE PREPARE stmt_drop_col;

-- 4. Supprimer la table des rôles.
DROP TABLE IF EXISTS roles;
