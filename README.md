# TechShop Manager

Application JavaFX 21 + MariaDB/MySQL pour la gestion d'un magasin de téléphonie
(stock, ventes, SAV/réparations, caisse, rapports).

## Prérequis
- JDK 21 ou plus récent (avec `jpackage` pour le packaging)
- MariaDB/MySQL local (XAMPP reste utilisable pendant le développement)
- Maven 3.9+

## Installation rapide
1. Démarrer le serveur MariaDB/MySQL local pour le développement.
2. Créer la base : `CREATE DATABASE techshop CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
3. Copier `db.properties.example` vers `config/db.properties`.
4. Lancer : `mvn javafx:run`.

## Premier lancement
- Au premier lancement, l'écran de licence affiche l'identifiant de l'ordinateur.
  Importer le fichier `.lic` fourni par le générateur avant de continuer.
- La licence est stockée dans `%LOCALAPPDATA%\TechShopManager\license\techshop-manager.lic`.
  La vérification est hors ligne (clé publique embarquée, aucune connexion réseau).
- Après activation, l'assistant crée le compte Gérant et les informations du magasin.
- Aucun identifiant ou mot de passe de production n'est prédéfini.

## Comptes et accès
- Le module Utilisateurs, les rôles et la matrice de permissions ont été retirés.
- La connexion reste obligatoire ; tous les comptes partagent le profil « Gérant ».
- La migration `V011` supprime les tables `roles` et `permissions` ainsi que la
  colonne `users.role_id`, sans supprimer les comptes ni l'historique.

## Profils de données

Le profil `prod` est utilisé par défaut et n'insère aucun produit, fournisseur,
client, vente ou réparation fictif. Pour une démonstration locale uniquement :

```text
mvn -Dtechshop.profile=demo javafx:run
```

Le profil demo ne doit pas être utilisé pour une installation client.

## Sauvegarde / Restauration
- Le chemin mysqldump/mysql est configurable dans Paramètres > Sauvegarde
- Le chemin des outils MariaDB/MySQL est configurable dans Paramètres > Sauvegarde.

## Packaging Windows (jpackage)
Le profil `jpackage` produit par défaut une application portable (dossier avec
`TechShopManager.exe` et runtime Java embarqué), sans dépendance à WiX :

```text
mvn -DskipTests -Pjpackage verify
```

Résultat : `target/dist/TechShopManager/TechShopManager.exe`.

Pour produire un véritable installeur Windows (nécessite WiX Toolset) :

```text
mvn -DskipTests -Pjpackage -Djpackage.type=EXE verify
mvn -DskipTests -Pjpackage -Djpackage.type=MSI verify
```

## Dépannage
- Erreur de connexion DB : vérifier MariaDB/MySQL sur le port configuré et les
  identifiants de `config/db.properties`
- Si schéma manquant : relancer l'app (MigrationService applique les versions)
- Thèmes : basculer dans Paramètres > Apparence