# Audit de livraison — TechShop Manager

## Périmètre

Audit réalisé avant la transformation de la démonstration en application
livrable mono-magasin et hors ligne. Le projet est une application JavaFX 21
avec Maven, JDBC/MariaDB, HikariCP et jBCrypt.

## Architecture existante

- `app` : démarrage JavaFX et coque d'application.
- `controller` : contrôleurs JavaFX et dialogues.
- `service` : logique métier, authentification, autorisation, navigation,
  sauvegarde.
- `dao` : accès JDBC avec requêtes préparées.
- `model` : entités métier et types de rôles.
- `config` : connexion, pool et migrations versionnées.
- `src/main/resources/db` : scripts SQL `V001` à `V007`.
- `src/test` : tests unitaires des services métier et de sécurité.

## Éléments de démonstration identifiés

### Comptes et secrets de démonstration

- `V002__initial_data.sql` crée le compte `admin`.
- Le mot de passe documenté est `admin123`.
- `V006__phase5.sql` crée `tech1`, `tech2` et `tech3`.
- Les comptes de démonstration utilisent une empreinte BCrypt connue.
- `README.md` documente encore `admin / admin123`.

### Données métier fictives

- `V003__product_seed.sql` contient des produits et mouvements de stock de
  démonstration.
- `V004__phase3_data.sql` contient des données métier de démonstration.
- `V006__phase5.sql` contient des techniciens, clients et réparations fictifs.
- Les données de démonstration sont actuellement exécutées par la chaîne de
  migration standard.

### Module Utilisateurs retiré

- L'écran Utilisateurs, `UsersController`, `UserService`, la matrice de
  permissions et l'onglet Journal d'activité ont été supprimés.
- `Role` ne contient plus que `ADMIN` (« Gérant »), profil unique de tous les
  comptes authentifiés.
- `SessionContext` ne gère plus de rôle d'affichage ni d'aperçu de rôle.
- La migration `V011__remove_users_roles_permissions.sql` supprime les tables
  `roles` et `permissions` ainsi que la colonne `users.role_id`. La table
  `users` est conservée pour la connexion et l'historique (`user_id`).

## Dépendances d'installation identifiées

- La base est actuellement une instance MariaDB/MySQL externe.
- `README.md` exige XAMPP.
- La configuration par défaut utilise `root` sans mot de passe.
- `BackupService` cherche par défaut `mysqldump` et `mysql`, avec un chemin
  XAMPP configurable.
- `DatabaseManager` crée la base via la connexion serveur configurée.
- Les chemins de configuration sont relatifs au répertoire de travail
  (`config/db.properties`), et non encore `%APPDATA%`.

## Sécurité existante

- Les mots de passe applicatifs sont hachés avec BCrypt.
- Les requêtes DAO inspectées utilisent des paramètres JDBC.
- Le verrouillage intervient après cinq échecs pendant quinze minutes.
- Les connexions, échecs et changements de mot de passe sont journalisés.
- La session est conservée en mémoire et vidée lors de la déconnexion.

## Risques à traiter dans les phases suivantes

1. Supprimer les comptes et données de démonstration de l'installation neuve,
   sans modifier rétroactivement les migrations déjà appliquées.
2. Ajouter l'assistant de première configuration et créer le premier compte
   hors ligne, sans identifiant prédéfini.
3. Persister l'état d'installation et protéger l'initialisation concurrente.
4. Remplacer la dépendance XAMPP par une base portable ou un repli
   d'installation clairement diagnostiqué.
5. Déplacer les données applicatives, journaux et secrets hors du dossier
   d'installation.

## État de référence

La suite de tests existante a été exécutée avec `mvn test` :

- 75 tests exécutés
- 0 échec
- 0 erreur

Ce résultat valide l'état courant après retrait du module Utilisateurs ; il ne
valide pas encore la totalité des parcours métier sur une base de production.
