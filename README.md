# TechShop Manager

Application JavaFX 21 + MariaDB/MySQL pour la gestion d'un magasin de téléphonie
(stock, ventes, SAV/réparations, caisse, rapports).

## Prérequis
- Java 21 (JDK)
- MariaDB/MySQL local (XAMPP reste utilisable pendant le développement)
- Maven 3.9+

## Installation rapide
1. Démarrer le serveur MariaDB/MySQL local pour le développement.
2. Créer la base : `CREATE DATABASE techshop CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
3. Copier `db.properties.example` vers `config/db.properties`.
4. Lancer : `mvn javafx:run`.

## Premier lancement
- Au premier lancement, l'assistant crée le compte Gérant et les informations du magasin.
- Aucun identifiant ou mot de passe de production n'est prédéfini.

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
Voir configuration Maven pour jpackage. Génère un installeur `.msi`/`.exe` avec runtime Java embarqué.

## Dépannage
- Erreur de connexion DB : vérifier MariaDB/MySQL sur le port configuré et les
  identifiants de `config/db.properties`
- Si schéma manquant : relancer l'app (MigrationService applique les versions)
- Thèmes : basculer dans Paramètres > Apparence