# TechShop Manager

Application JavaFX 21 + SQLite pour la gestion d'un magasin de téléphonie
(stock, ventes, SAV/réparations, caisse, rapports). Aucun serveur de base de
données n'est nécessaire : la base est un fichier SQLite persistant.

## Prérequis (développement uniquement)
- JDK 21 ou plus récent (avec `jpackage` pour le packaging)
- Maven 3.9+

L'application installée ne requiert ni Java, ni MySQL/MariaDB, ni Docker.

## Installation rapide (développement)
1. Lancer : `mvn javafx:run`.
2. La base est créée automatiquement au premier démarrage.

Aucune création manuelle de base ni aucun fichier de configuration ne sont
nécessaires. Le fichier `config/db.properties` est facultatif et ne contient
aucun secret.

## Emplacement des données
- Base : `%APPDATA%\TechShopManager\data\techshop.db`
- Sauvegardes : `%APPDATA%\TechShopManager\backups`
- Licence : `%LOCALAPPDATA%\TechShopManager\license\techshop-manager.lic`

Ces emplacements sont indépendants du dossier d'installation et du programme :
une mise à jour ou une désinstallation ne supprime pas les données client.
La base existante n'est jamais réinitialisée automatiquement.

## Premier lancement
- L'écran de licence affiche l'identifiant de l'ordinateur. Importer le fichier
  `.lic` fourni par le générateur avant de continuer. La vérification est hors
  ligne (clé publique embarquée, aucune connexion réseau).
- Après activation, l'assistant crée le compte Gérant et les informations du
  magasin. Aucun identifiant de production n'est prédéfini.

## Schéma et migrations
- Les scripts SQLite se trouvent dans `src/main/resources/db/sqlite/`.
- `V001__schema.sql` crée le schéma complet (tables, index, contraintes,
  déclencheurs `updated_at`).
- `V002__reference_data.sql` insère les données de référence (catégories,
  paramètres) et est appliqué en production.
- `V003__demo_data.sql` est réservé au profil de démonstration.
- Chaque migration est enregistrée dans la table `schema_version`
  (version, description, empreinte SHA-256, date, durée) et exécutée dans une
  transaction. Les futures migrations `V004__…` seront appliquées
  automatiquement au démarrage.

## Profils de données
Le profil `prod` est utilisé par défaut et n'insère aucun produit, fournisseur,
client, vente ou réparation fictif. Pour une démonstration locale uniquement :

```text
mvn -Dtechshop.profile=demo javafx:run
```

Le profil demo ne doit pas être utilisé pour une installation client.

## Sauvegarde / Restauration
- Paramètres > Sauvegarde permet de créer une copie SQLite cohérente
  (`VACUUM INTO`) et de restaurer un fichier `.db`.
- Avant toute restauration, une sauvegarde de sécurité est créée.
- Le fichier choisi est validé (`integrity_check`, présence du schéma) avant de
  remplacer la base. En cas d'échec, l'ancienne base est remise en place.
- La sauvegarde automatique à la fermeture est optionnelle.

## Migration depuis MySQL/MariaDB
Un outil ponctuel copie une base MySQL/MariaDB existante vers SQLite en
conservant les identifiants et les relations. La base source n'est jamais
modifiée.

```text
mvn -Pmysql-migration compile exec:java -Dexec.args="--source-url jdbc:mariadb://localhost:3306/techshop --source-user root --source-password secret --target C:\chemin\techshop.db"
```

L'outil :
1. refuse d'écraser un fichier non vide ;
2. crée le schéma SQLite (`V001`) ;
3. copie toutes les tables communes en conservant les clés primaires ;
4. vérifie le nombre de lignes et `PRAGMA foreign_key_check` ;
5. marque les données de référence/démo comme déjà présentes.

Le pilote MariaDB n'est présent que dans le profil `mysql-migration` ; il n'est
pas embarqué dans l'installateur client.

## Packaging Windows (jpackage)
Le profil `jpackage` produit une application autonome (runtime Java embarqué)
sans dépendance à WiX :

```text
mvn -DskipTests -Pjpackage verify
```

Résultat : `target/dist/TechShop Manager/TechShop Manager.exe`.

Pour produire un installeur Windows `.exe` ou `.msi` (nécessite WiX Toolset
3.x, avec `candle.exe` et `light.exe` dans le `PATH`) :

```text
mvn -DskipTests -Pjpackage,installer verify
mvn -DskipTests -Pjpackage,installer -Djpackage.type=MSI verify
```

Résultats :

- `target/dist/TechShop Manager-1.0.0.exe` (installateur EXE)
- `target/dist/TechShop Manager-1.0.0.msi` (installateur MSI)

Le profil `installer` active les raccourcis, le menu Démarrer, le choix du
dossier et l'UUID de mise à jour. Ces options sont désactivées pour
`APP_IMAGE`, car jpackage les refuse avec ce type.

L'application installée fonctionne hors ligne, embarque son runtime Java et
conserve sa base dans `%APPDATA%\TechShopManager\data\techshop.db`. Une mise à
jour ou une désinstallation ne supprime pas ce fichier.

## Dépannage
- Erreur de base : vérifier que `%APPDATA%\TechShopManager\data` est accessible
  en écriture et qu'une seule instance de l'application est ouverte.
- Si le schéma est incomplet : redémarrer l'application (les migrations sont
  appliquées automatiquement).
- Thèmes : basculer dans Paramètres > Apparence.
