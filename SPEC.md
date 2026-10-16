# 1. STACK TECHNIQUE
- Java 21, JavaFX 21, Maven. Interface en FXML + CSS JavaFX (pas de logique métier dans les contrôleurs).
- Base : MySQL via XAMPP (compatible MariaDB : n'utilise aucune fonction exclusive à MySQL 8). Hôte localhost:3306, base "techshop", utilisateur root sans mot de passe par défaut, le tout configurable dans config/db.properties.
- JDBC pur avec HikariCP (pool), pattern DAO + Service + Contrôleur. Requêtes préparées partout. Transactions pour toute opération touchant plusieurs tables.
- Bibliothèques : jBCrypt (mots de passe), Ikonli (icônes Feather), OpenPDF (PDF), Apache POI (Excel), ZXing (codes-barres), SLF4J + Logback (logs), JUnit 5 (tests des services).
- Architecture en packages : app, config, model, dao, service, controller, ui (composants réutilisables), util, resources (fxml, css, images, i18n).
- Un seul point d'entrée, un navigateur d'écrans central (NavigationService) qui charge les vues FXML dans la zone de contenu.
- Langue : français. Devise : dinar algérien, format "12 500,00 DA". Dates : JJ/MM/AAAA. Chiffres des montants alignés à droite.

# 2. DESIGN SYSTEM
Identité : outil premium, rapide, sobre, esprit Linear / Stripe Dashboard. Pas un template d'admin générique. Réalisable en JavaFX CSS : pas de flou, pas d'animations complexes, ombres douces autorisées (DropShadow).

Jetons de couleur (définis comme variables CSS JavaFX, ex. -fx-primary, dans theme-light.css et theme-dark.css, plus un base.css commun) :
- Thème clair : fond #F8FAFC, surface #FFFFFF, surface secondaire #F1F5F9, bordure #E2E8F0, texte principal #0F172A, texte secondaire #64748B, primaire #4F46E5, primaire survolé #4338CA, primaire léger #EEF2FF, succès #16A34A (fond #DCFCE7), alerte #F59E0B (fond #FEF3C7), erreur #DC2626 (fond #FEE2E2), info #2563EB (fond #DBEAFE).
- Thème sombre : fond #0B1120, surface #111C2E, surface secondaire #1E293B, bordure #263449, texte principal #F1F5F9, texte secondaire #94A3B8, primaire #6366F1, mêmes couleurs sémantiques éclaircies avec fonds translucides.
- Bascule clair/sombre à chaud depuis la barre supérieure, préférence enregistrée.

Typographie : Inter (embarque la police dans resources), repli Segoe UI. Titre de page 24 px semi-gras, sous-titre 14 px, libellé 12 px majuscules espacées, corps 13-14 px, grands chiffres 28-32 px gras. Montants en chiffres tabulaires.

Forme : grille de 8 px. Cartes : rayon 12, bordure 1 px, ombre légère. Boutons et champs : rayon 8, hauteur 36 (compact 30). Dialogues : rayon 16, ombre plus forte, fond assombri derrière.

Composants à créer comme classes réutilisables (package ui) avec leurs styles CSS :
- Boutons : principal (plein indigo), secondaire (surface + bordure), contour, danger, fantôme, icône seule ; états survol, appuyé, désactivé, focus visible.
- Champs : texte, mot de passe avec œil, nombre, date, liste déroulante, recherche avec icône, zone de texte ; états normal, focus (anneau indigo), erreur (bordure rouge + message dessous), désactivé.
- Badges de statut arrondis (succès, alerte, erreur, info, neutre).
- Carte statistique (libellé, grand chiffre, variation ▲ vert / ▼ rouge, mini-courbe optionnelle).
- Tableau (TableView stylé) : en-tête surface secondaire, lignes 44 px, survol, sélection indigo léger, ligne d'alerte, tri, pagination en pied, compteur de résultats, menu d'actions par ligne.
- Avatar à initiales colorées, onglets soulignés, interrupteur, case à cocher, puces de filtre.
- Dialogues modaux (confirmation, formulaire, formulaire large à onglets), notifications "toast" (succès, erreur, info) en haut à droite, états vides (icône + message + bouton), indicateur de chargement.
- Raccourcis clavier affichés en petites touches à côté des actions principales.

Structure de l'interface :
- Barre latérale gauche fixe : 248 px dépliée, 72 px repliée (icônes seules, bouton de repli). Logo + nom en haut, entrées avec icône, entrée active surlignée (fond indigo léger + barre indigo à gauche), utilisateur connecté en bas. Menu filtré selon le rôle.
- Barre supérieure (64 px) : titre de page + fil d'Ariane, bouton "Rechercher… Ctrl+K" (palette de commandes : produits, clients, ventes, réparations, actions rapides), bascule thème, cloche avec compteur (alertes de stock, réparations prêtes, dettes en retard), indicateur "Caisse ouverte/fermée", avatar + nom + rôle, déconnexion.
- Zone de contenu avec marges de 24 px, défilement vertical interne.
- Fenêtre redimensionnable, taille minimale 1280x720, adaptée de 1366x768 à 1920x1080.

# 3. RÔLES ET PERMISSIONS
- ADMIN : accès à tout.
- VENDEUR : point de vente, ventes (siennes), clients, consultation produits et stock, sa propre caisse.
- TECHNICIEN : SAV/réparations, pièces, consultation produits et stock.
Permissions par module stockées en base (table permissions) et vérifiées dans les services, pas seulement masquées dans l'interface.

# 4. MODULES ET ÉCRANS
1. Connexion : écran scindé (marque à gauche, formulaire à droite), message d'erreur, blocage temporaire après 5 échecs.
2. Tableau de bord : cartes (ventes du jour, bénéfice du jour, ticket moyen, réparations en cours, valeur du stock, dettes clients) avec variation vs veille ; courbe des ventes 7/30 jours ; anneau par catégorie ; top 5 produits ; dernières ventes ; panneau d'alertes ; raccourcis (Nouvelle vente, Nouvelle réparation, Ajouter produit, Réception de stock).
3. Point de vente (écran le plus soigné) : deux colonnes. Gauche ~62 % : champ recherche/scan code-barres toujours actif (F2), puces de catégories, grille de cartes produits (image, nom, prix, stock, badge stock faible) avec bascule grille/liste. Droite ~38 % : onglets de paniers en attente, lignes du panier (quantité +/-, prix, remise par ligne, suppression), client (F4) avec ajout rapide, remise globale (F8), sous-total, total en très grand, boutons de paiement (Espèces, Carte, Virement, Crédit/Partiel), montant reçu avec pavé numérique et rendu de monnaie, gros bouton "Encaisser (F10)". Saisie IMEI obligatoire pour les produits suivis par IMEI, avec garantie. Ticket 80 mm en aperçu et impression (PDF).
4. Ventes : cartes de synthèse, tableau filtrable (n°, date, client, vendeur, total, mode, statut Payée/Partielle/Impayée/Remboursée), détail, impression ticket et facture A4, retour/remboursement partiel ou total avec remise en stock.
5. Produits : tableau (miniature, référence, code-barres, nom, catégorie, marque, prix achat, prix vente, marge %, quantité, statut), filtres, bascule tableau/grille, formulaire à onglets (Général, Prix, Stock, Options IMEI/garantie), gestion catégories et marques, génération et impression d'étiquettes code-barres, import CSV.
6. Stock : cartes (valeur, nombre d'articles, stock faible, ruptures), onglets Alertes (avec bouton Commander), Mouvements (entrées, sorties, ajustements, pertes), Inventaire (quantité système, comptée, écart, validation), Appareils IMEI (statut en stock/vendu/en réparation).
7. Achats & Fournisseurs : fournisseurs (solde dû), bons de commande (Brouillon, Envoyé, Reçu partiellement, Reçu), réception de marchandise (met à jour stock et prix d'achat moyen pondéré), paiements fournisseurs.
8. Clients : liste (avatar, nom, téléphone, nb achats, total, dette), fiche (coordonnées, onglets Achats, Réparations, Paiements et dettes), encaissement de dette (partiel/total) avec reçu.
9. SAV/Réparations (écran signature) : vue Kanban par statut (Reçu, Diagnostic, En attente de pièce, En réparation, Prête, Livrée, Annulée) avec glisser-déposer et vue tableau ; dépôt en 4 étapes (client, appareil+état à la réception, panne+devis, acompte+date prévue+technicien) ; fiche (chronologie des statuts, diagnostic, pièces utilisées avec déduction du stock, main-d'œuvre, total, acomptes, reste à payer, garantie réparation, notes internes, photos) ; bon de dépôt et bon de retrait imprimables avec code-barres ; remise de l'appareil avec paiement du solde ; notification client simulée (journalisée).
10. Caisse & Finances : ouverture (fonds initial), clôture (espèces attendues vs comptées par coupures, écart coloré), entrées/sorties de caisse avec motif, dépenses par catégorie, journal des opérations, résumé journalier, graphique recettes/dépenses.
11. Rapports : ventes par période, bénéfices, produits les plus vendus, stock valorisé, performance vendeurs/techniciens, réparations (par statut, délai moyen), dettes clients/fournisseurs. Filtres de dates, graphiques, export PDF/Excel, impression.
12. Utilisateurs : liste, création/modification, matrice de permissions, activation/désactivation, réinitialisation du mot de passe, journal d'activité.
13. Paramètres : magasin (nom, logo, adresse, téléphone, mentions du ticket), ventes et taxes (TVA, numérotation), impression, seuils de stock par défaut, sauvegarde et restauration de la base (mysqldump), apparence (thème, taille de police), à propos.

# 5. MODÈLE DE DONNÉES (à implémenter via scripts SQL versionnés dans resources/db/)
Tables : roles, users, permissions, activity_log, settings, categories, brands, products (ref, barcode unique, prix achat, prix vente, tva, seuil alerte, suivi_imei, garantie_mois, actif), product_units (IMEI/numéro de série unique, statut), stock_movements (type, quantité, motif, référence liée, utilisateur, date), suppliers, purchase_orders, purchase_order_items, goods_receipts, supplier_payments, customers, sales, sale_items, sale_payments, sale_returns, cash_sessions, cash_movements, expense_categories, repair_tickets, repair_status_history, repair_parts, repair_payments, repair_attachments, notifications_log.
Contraintes : clés étrangères, index sur code-barres, IMEI, dates, numéros de documents. Suppressions logiques (champ actif) pour produits, clients, utilisateurs. Numérotation séquentielle par type (V-2026-000001, R-2026-000001, A-2026-000001).

# 6. RÈGLES MÉTIER
- Une vente est une transaction unique : lignes, paiements, mouvements de stock, mise à jour du statut des IMEI, dette client, tout ou rien.
- Le stock ne devient jamais négatif (sauf option explicite dans les paramètres).
- Un produit suivi par IMEI : une unité vendue = un IMEI précis ; impossible de vendre un IMEI déjà vendu.
- Le prix d'achat moyen pondéré est recalculé à chaque réception ; le bénéfice d'une vente utilise le coût au moment de la vente.
- Paiement partiel = dette client ; encaissement de dette lié à une session de caisse.
- Pas de vente possible si aucune session de caisse n'est ouverte pour le vendeur.
- Une réparation consomme des pièces du stock (mouvement de sortie lié au dossier) ; livraison = solde payé ou mis en dette.
- Toute action sensible (suppression, remboursement, ajustement de stock, changement de prix, clôture de caisse) est écrite dans activity_log.
- Validation de tous les formulaires (champs obligatoires, formats, doublons) avec messages sous les champs.

# 7. QUALITÉ
- Code propre, commenté en français quand utile, gestion d'erreurs avec messages clairs pour l'utilisateur, logs techniques à part.
- Tâches longues (exports, sauvegardes, requêtes lourdes) hors du thread JavaFX avec indicateur de chargement.
- Tests JUnit sur les services critiques (vente, stock, caisse, réparation).
- Données de démonstration réalistes (marques de téléphones courantes, noms algériens, prix en DA), chargeables via un script dédié.
