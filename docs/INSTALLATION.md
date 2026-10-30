# Installation développeur — TechShop Manager

## Runtime de base de données

L'application cherche d'abord un runtime MariaDB portable Windows sous :

```text
%APPDATA%\TechShop Manager\runtime\mariadb\
```

Le dossier attendu contient `bin\mariadbd.exe`, `bin\mysqldump.exe`,
`bin\mysql.exe` et un datadir initialisé sous `data\mysql\`.

Le serveur est limité à `127.0.0.1` et utilise le port local `3307` par
défaut. Les données persistantes sont conservées sous
`%APPDATA%\TechShop Manager\data\`.

Le dépôt ne contient pas les binaires MariaDB. Ils doivent être fournis lors
du packaging de la release conformément à leur licence de distribution.

Si le runtime portable est absent ou non initialisé, l'application conserve le
repli vers le serveur MariaDB/MySQL configuré dans
`%APPDATA%\TechShop Manager\config\db.properties`.

## Vérification avant release

1. Placer un runtime MariaDB compatible dans le dossier attendu.
2. Initialiser son datadir avec un utilisateur applicatif dédié.
3. Vérifier que `mariadbd.exe` écoute uniquement sur `127.0.0.1:3307`.
4. Lancer l'application hors connexion Internet.
5. Vérifier l'assistant de première configuration.
6. Vérifier les sauvegardes avec les utilitaires embarqués.
7. Retirer le runtime et vérifier le diagnostic du repli externe.

L'initialisation automatique du datadir et l'intégration des binaires dans
l'installeur seront finalisées dans la phase de packaging Windows.
