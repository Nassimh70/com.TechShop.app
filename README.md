# TechShop Manager

Application JavaFX 21 + MySQL pour la gestion d'un magasin de téléphonie (stock, ventes, SAV/réparations, caisse, rapports).

## Prérequis
- Java 21 (JDK)
- XAMPP (Apache + MySQL/MariaDB)
- Maven 3.9+

## Installation rapide
1. Démarrer XAMPP et activer MySQL
2. Créer la base : `C:\xampp\mysql\bin\mysql.exe -u root -e "CREATE DATABASE techshop CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"`
3. Copier `db.properties.example` vers `src/main/resources/config/db.properties` (ou `config/db.properties` selon lancement)
4. Lancer : `mvn javafx:run`

## Premier lancement
- Compte admin par défaut : `admin` / `admin123` (selon données de démo existantes)
- Configurer l'application via Paramètres (magasin, TVA, numérotation)

## Sauvegarde / Restauration
- Le chemin mysqldump/mysql est configurable dans Paramètres > Sauvegarde
- Par défaut XAMPP : `C:\xampp\mysql\bin`

## Packaging Windows (jpackage)
Voir configuration Maven pour jpackage. Génère un installeur `.msi`/`.exe` avec runtime Java embarqué.

## Dépannage
- Erreur de connexion DB : vérifier MySQL/XAMPP sur 3306, utilisateur root sans mot de passe
- Si schéma manquant : relancer l'app (MigrationService applique les versions)
- Thèmes : basculer dans Paramètres > Apparence