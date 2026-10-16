package com.techshop.service;

import com.techshop.config.Tx;
import com.techshop.dao.ProductDao;
import com.techshop.model.ActivityActions;
import com.techshop.model.Brand;
import com.techshop.model.Category;
import com.techshop.model.Product;
import com.techshop.model.StockMovementType;
import com.techshop.util.Images;
import com.techshop.util.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gestion du catalogue produits (SPEC §4.5, §6.1) : recherche filtrée et
 * paginée, création/modification, code-barres automatique, image, import CSV.
 * Toutes les règles métier et les droits sont vérifiés ici, pas dans l'interface.
 */
public final class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private static final ProductService INSTANCE =
            new ProductService(new ProductDao(), ActivityLogService.get(), StockService.get());

    private final ProductDao products;
    private final ActivityAudit journal;
    private final StockService stock;

    public ProductService(ProductDao products, ActivityAudit journal, StockService stock) {
        this.products = products;
        this.journal = journal;
        this.stock = stock;
    }

    public static ProductService get() {
        return INSTANCE;
    }

    /** Page de résultats (pagination). */
    public record Page(List<Product> items, long total, int page, int size) {

        public int pageCount() {
            return size <= 0 ? 1 : (int) Math.max(1, (total + size - 1) / size);
        }
    }

    /** Rapport d'import CSV. */
    public record CsvReport(int total, int created, int updated, List<String> errors) {

        public boolean sansErreur() {
            return errors.isEmpty();
        }
    }

    // --------------------------------------------------------------- lectures

    public Page search(ProductDao.Filter filter, int page, int size, String sortKey, boolean asc) {
        int p = Math.max(1, page);
        int t = Math.max(1, size);
        try {
            long total = products.count(filter);
            List<Product> items = products.search(filter, sortKey, asc, t, (p - 1) * t);
            return new Page(items, total, p, t);
        } catch (SQLException e) {
            log.error("Recherche de produits impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    public Product byId(int id) {
        try {
            return products.findById(id).orElseThrow(
                    () -> new ValidationException("Produit introuvable."));
        } catch (SQLException e) {
            log.error("Lecture du produit {} impossible", id, e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    public List<Product> allActive() {
        try {
            return products.allActive();
        } catch (SQLException e) {
            log.error("Lecture des produits impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    // -------------------------------------------------------------- écritures

    /** Crée un produit ; la quantité initiale devient une entrée de stock. */
    public int create(Product p) {
        PermissionService.get().check(PermissionDefaults.M_PRODUCTS, PermissionService.Action.CREATE);
        valider(p);
        try {
            return Tx.run(() -> creerInterne(p));
        } catch (SQLException e) {
            log.error("Création de produit impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    /** Modifie un produit (la quantité se gère via le module Stock). */
    public void update(Product p) {
        PermissionService.get().check(PermissionDefaults.M_PRODUCTS, PermissionService.Action.EDIT);
        valider(p);
        try {
            Product existant = products.findById(p.getId()).orElseThrow(
                    () -> new ValidationException("Produit introuvable."));
            normaliserBarcode(p);
            if (p.getBarcode() != null && products.barcodeExists(p.getBarcode(), p.getId())) {
                throw new ValidationException("barcode", "Ce code-barres est déjà utilisé.");
            }
            boolean prixChange = p.getSalePrice().compareTo(existant.getSalePrice()) != 0;
            products.update(p);
            journal.log(userId(), ActivityActions.MODIFICATION, "products", p.getId(),
                    "Modification de « " + p.getName() + " » (" + p.getRef() + ")");
            if (prixChange) {
                journal.log(userId(), ActivityActions.CHANGEMENT_PRIX, "products", p.getId(),
                        "Prix de « " + p.getName() + " » : " + existant.getSalePrice()
                                + " → " + p.getSalePrice());
            }
        } catch (SQLException e) {
            log.error("Modification de produit impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    public void setActive(int id, boolean active) {
        PermissionService.get().check(PermissionDefaults.M_PRODUCTS, PermissionService.Action.EDIT);
        try {
            products.setActive(id, active);
            journal.log(userId(), active ? ActivityActions.ACTIVATION : ActivityActions.DESACTIVATION,
                    "products", id, (active ? "Activation" : "Désactivation") + " d'un produit");
        } catch (SQLException e) {
            log.error("Changement de statut du produit {} impossible", id, e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    /**
     * Génère un code-barres EAN-13 pour un produit qui n'en a pas.
     *
     * @return le code-barres (existant ou nouvellement généré)
     */
    public String generateBarcode(int id) {
        PermissionService.get().check(PermissionDefaults.M_PRODUCTS, PermissionService.Action.EDIT);
        try {
            Product p = products.findById(id).orElseThrow(
                    () -> new ValidationException("Produit introuvable."));
            if (p.getBarcode() != null && !p.getBarcode().isBlank()) {
                return p.getBarcode();
            }
            String code = BarcodeService.generateEan13(id);
            products.setBarcode(id, code);
            journal.log(userId(), ActivityActions.MODIFICATION, "products", id,
                    "Code-barres généré : " + code);
            return code;
        } catch (SQLException e) {
            log.error("Génération de code-barres impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    /** Génère les codes-barres manquants de tout le catalogue. */
    public int generateMissingBarcodes() {
        PermissionService.get().check(PermissionDefaults.M_PRODUCTS, PermissionService.Action.EDIT);
        try {
            List<Product> sansCode = products.withoutBarcode();
            for (Product p : sansCode) {
                products.setBarcode(p.getId(), BarcodeService.generateEan13(p.getId()));
            }
            if (!sansCode.isEmpty()) {
                journal.log(userId(), ActivityActions.MODIFICATION, "products", null,
                        "Génération de " + sansCode.size() + " code(s)-barres manquant(s)");
            }
            return sansCode.size();
        } catch (SQLException e) {
            log.error("Génération des codes-barres manquants impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    /** Copie une image dans l'application et l'associe au produit. */
    public void setImage(int id, Path source) {
        PermissionService.get().check(PermissionDefaults.M_PRODUCTS, PermissionService.Action.EDIT);
        try {
            Product p = products.findById(id).orElseThrow(
                    () -> new ValidationException("Produit introuvable."));
            String chemin = Images.copyIn(source, p.getRef());
            products.setImagePath(id, chemin);
            journal.log(userId(), ActivityActions.MODIFICATION, "products", id,
                    "Image modifiée pour « " + p.getName() + " »");
        } catch (IOException e) {
            throw new ValidationException("Image illisible : " + e.getMessage());
        } catch (SQLException e) {
            log.error("Enregistrement de l'image impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    // ------------------------------------------------------------- import CSV

    /**
     * Importe un fichier CSV de produits (séparateur « ; », en-tête obligatoire).
     * Colonnes reconnues : ref, nom, categorie, marque, code_barres, prix_achat,
     * prix_vente, tva, seuil, quantite, imei, garantie, unite.
     * Une référence existante met à jour le produit ; sinon il est créé.
     */
    public CsvReport importCsv(Path fichier) {
        PermissionService.get().check(PermissionDefaults.M_PRODUCTS, PermissionService.Action.CREATE);
        List<String> erreurs = new ArrayList<>();
        int total = 0;
        int crees = 0;
        int maj = 0;
        try {
            List<String> lignes = Files.readAllLines(fichier, StandardCharsets.UTF_8);
            if (lignes.isEmpty()) {
                throw new ValidationException("Fichier vide.");
            }
            List<String> entetes = decouper(lignes.get(0).replace("\uFEFF", ""));
            Map<String, Integer> index = indexer(entetes);
            if (!index.containsKey("nom") || !index.containsKey("prix_vente")) {
                throw new ValidationException(
                        "En-tête invalide : colonnes « nom » et « prix_vente » requises.");
            }
            Map<String, Integer> categories = nomsCategories();
            Map<String, Integer> marques = nomsMarques();

            for (int i = 1; i < lignes.size(); i++) {
                String brut = lignes.get(i);
                if (brut.isBlank()) {
                    continue;
                }
                total++;
                int numero = i + 1;
                try {
                    List<String> cols = decouper(brut);
                    boolean misAJour = importerLigne(index, cols, categories, marques);
                    if (misAJour) {
                        maj++;
                    } else {
                        crees++;
                    }
                } catch (ValidationException e) {
                    erreurs.add("Ligne " + numero + " : " + e.getMessage());
                } catch (RuntimeException e) {
                    erreurs.add("Ligne " + numero + " : donnée invalide (" + e.getMessage() + ")");
                }
            }
            journal.log(userId(), ActivityActions.IMPORT_CSV, "products", null,
                    "Import CSV : " + crees + " créé(s), " + maj + " mis à jour, "
                            + erreurs.size() + " erreur(s)");
            return new CsvReport(total, crees, maj, erreurs);
        } catch (IOException e) {
            throw new ValidationException("Lecture du fichier impossible : " + e.getMessage());
        } catch (SQLException e) {
            log.error("Import CSV impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    // ----------------------------------------------------------------- interne

    /** @return true si le produit existait (mise à jour), false si créé. */
    private boolean importerLigne(Map<String, Integer> index, List<String> cols,
                                  Map<String, Integer> categories, Map<String, Integer> marques)
            throws SQLException {
        String nom = valeur(cols, index, "nom");
        if (nom == null) {
            throw new ValidationException("nom manquant");
        }
        String ref = valeur(cols, index, "ref");
        Product existant = ref == null ? null : products.findByRef(ref).orElse(null);

        Product p = existant == null ? new Product() : existant;
        p.setName(nom);
        String barcode = valeur(cols, index, "code_barres");
        if (barcode != null) {
            p.setBarcode(barcode);
        }
        p.setPurchasePrice(decimalOu(valeur(cols, index, "prix_achat"), p.getPurchasePrice()));
        BigDecimal prixVente = decimalOu(valeur(cols, index, "prix_vente"), null);
        if (prixVente == null) {
            throw new ValidationException("prix_vente requis");
        }
        p.setSalePrice(prixVente);
        p.setVatRate(decimalOu(valeur(cols, index, "tva"), p.getVatRate()));
        Integer seuil = entier(valeur(cols, index, "seuil"));
        if (seuil != null) {
            p.setAlertThreshold(seuil);
        }
        String imei = valeur(cols, index, "imei");
        if (imei != null) {
            p.setTrackImei(estVrai(imei));
        }
        Integer garantie = entier(valeur(cols, index, "garantie"));
        if (garantie != null) {
            p.setWarrantyMonths(garantie);
        }
        String unite = valeur(cols, index, "unite");
        if (unite != null) {
            p.setUnit(unite);
        }

        String categorie = valeur(cols, index, "categorie");
        if (categorie != null) {
            Integer id = categories.get(normaliser(categorie));
            if (id == null) {
                throw new ValidationException("catégorie inconnue « " + categorie + " »");
            }
            p.setCategoryId(id);
        }
        String marque = valeur(cols, index, "marque");
        if (marque != null) {
            Integer id = marques.get(normaliser(marque));
            if (id == null) {
                throw new ValidationException("marque inconnue « " + marque + " »");
            }
            p.setBrandId(id);
        }

        valider(p);
        if (existant != null) {
            // p est déjà l'instance chargée : référence et identifiant intacts.
            normaliserBarcode(p);
            if (p.getBarcode() != null && products.barcodeExists(p.getBarcode(), p.getId())) {
                throw new ValidationException("code-barres déjà utilisé");
            }
            products.update(p);
            return true;
        }
        int quantite = index.containsKey("quantite") ? entierOu(valeur(cols, index, "quantite"), 0) : 0;
        p.setQuantity(quantite);
        creerInterne(p);
        return false;
    }

    /** Insertion interne (référence + code-barres générés, quantité → mouvement). */
    private int creerInterne(Product p) throws SQLException {
        normaliserBarcode(p);
        if (p.getBarcode() != null && products.barcodeExists(p.getBarcode(), 0)) {
            throw new ValidationException("barcode", "Ce code-barres est déjà utilisé.");
        }
        p.setRef("P-" + String.format("%06d", products.nextRefNumber()));
        int quantiteInitiale = Math.max(0, p.getQuantity());
        p.setQuantity(0);
        int id = products.insert(p);
        if (p.getBarcode() == null) {
            String code = BarcodeService.generateEan13(id);
            products.setBarcode(id, code);
            p.setBarcode(code);
        }
        if (quantiteInitiale > 0) {
            stock.apply(id, StockMovementType.ENTREE, quantiteInitiale, null,
                    p.getPurchasePrice(), "Stock initial", "MANUAL", null);
        }
        journal.log(userId(), ActivityActions.CREATION, "products", id,
                "Création de « " + p.getName() + " » (" + p.getRef() + ")");
        return id;
    }

    private Map<String, Integer> nomsCategories() throws SQLException {
        Map<String, Integer> map = new HashMap<>();
        for (Category c : CatalogService.get().categories(true)) {
            map.put(normaliser(c.name()), c.id());
        }
        return map;
    }

    private Map<String, Integer> nomsMarques() throws SQLException {
        Map<String, Integer> map = new HashMap<>();
        for (Brand b : CatalogService.get().brands(true)) {
            map.put(normaliser(b.name()), b.id());
        }
        return map;
    }

    private void valider(Product p) {
        if (p.getName() == null || p.getName().trim().length() < 2) {
            throw new ValidationException("name", "Le nom du produit est obligatoire.");
        }
        if (p.getSalePrice() == null || p.getSalePrice().signum() < 0) {
            throw new ValidationException("salePrice", "Le prix de vente doit être positif.");
        }
        if (p.getPurchasePrice() != null && p.getPurchasePrice().signum() < 0) {
            throw new ValidationException("purchasePrice", "Le prix d'achat doit être positif.");
        }
        if (p.getAlertThreshold() < 0) {
            throw new ValidationException("alertThreshold", "Le seuil d'alerte doit être positif.");
        }
        if (p.getWarrantyMonths() < 0) {
            throw new ValidationException("warrantyMonths", "La garantie doit être positive.");
        }
        if (p.getVatRate() == null || p.getVatRate().signum() < 0) {
            p.setVatRate(BigDecimal.ZERO);
        }
    }

    private static void normaliserBarcode(Product p) {
        if (p.getBarcode() != null && p.getBarcode().isBlank()) {
            p.setBarcode(null);
        }
    }

    private static Integer userId() {
        var u = SessionContext.get().getUser();
        return u == null ? null : u.getId();
    }

    // --- analyse du CSV ---

    private static List<String> decouper(String ligne) {
        List<String> champs = new ArrayList<>();
        StringBuilder courant = new StringBuilder();
        boolean guillemets = false;
        for (int i = 0; i < ligne.length(); i++) {
            char c = ligne.charAt(i);
            if (c == '"') {
                guillemets = !guillemets;
            } else if (c == ';' && !guillemets) {
                champs.add(courant.toString().trim());
                courant.setLength(0);
            } else {
                courant.append(c);
            }
        }
        champs.add(courant.toString().trim());
        return champs;
    }

    private static Map<String, Integer> indexer(List<String> entetes) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < entetes.size(); i++) {
            map.put(normaliser(entetes.get(i)), i);
        }
        return map;
    }

    private static String normaliser(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String valeur(List<String> cols, Map<String, Integer> index, String cle) {
        Integer i = index.get(cle);
        if (i == null || i >= cols.size()) {
            return null;
        }
        String v = cols.get(i);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static BigDecimal decimal(String v) {
        try {
            return new BigDecimal(v.replace(',', '.').replace(" ", ""));
        } catch (NumberFormatException e) {
            throw new ValidationException("nombre invalide « " + v + " »");
        }
    }

    private static BigDecimal decimalOu(String v, BigDecimal defaut) {
        return v == null ? defaut : decimal(v);
    }

    private static Integer entier(String v) {
        if (v == null) {
            return null;
        }
        return entierOu(v, 0);
    }

    private static int entierOu(String v, int defaut) {
        if (v == null || v.isBlank()) {
            return defaut;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return (int) Double.parseDouble(v.replace(',', '.'));
        }
    }

    private static boolean estVrai(String v) {
        String s = normaliser(v);
        return "1".equals(v) || s.equals("oui") || s.equals("true") || s.equals("vrai")
                || s.equals("o") || s.equals("v") || s.equals("yes");
    }
}
