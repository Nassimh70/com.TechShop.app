package com.techshop.controller;

import com.techshop.model.Product;
import com.techshop.service.ProductService;
import com.techshop.service.StockService;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.ui.components.ToastService;
import com.techshop.util.ValidationException;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dialogue de mouvement manuel de stock (SPEC §4.6) : entrée, sortie, perte,
 * retour ou ajustement. Le motif est obligatoire pour toute sortie et pour
 * l'ajustement (traçabilité) ; l'entrée accepte un coût unitaire.
 * Toutes les règles sont appliquées par {@link StockService}.
 */
public final class StockDialog {

    public enum Operation {
        ENTREE("Entrée de stock", "download", "Entrée"),
        SORTIE("Sortie de stock", "upload", "Sortie"),
        PERTE("Perte / casse", "alert-triangle", "Perte"),
        RETOUR("Retour en stock", "corner-up-left", "Retour"),
        AJUSTEMENT("Ajuster le stock", "sliders", "Ajuster");

        private final String titre;
        private final String icone;
        private final String action;

        Operation(String titre, String icone, String action) {
            this.titre = titre;
            this.icone = icone;
            this.action = action;
        }
    }

    private final Operation operation;
    private final Product initial;
    private final Runnable onDone;
    private final ProductService produits = ProductService.get();
    private final StockService stock = StockService.get();

    private ComboBox<Product> cProduit;
    private FormField fQuantite;
    private FormField fCout;
    private FormField fMotif;
    private Label erreur;
    private Label contexte;

    private StockDialog(Operation operation, Product initial, Runnable onDone) {
        this.operation = operation;
        this.initial = initial;
        this.onDone = onDone;
    }

    public static void ouvrir(Operation operation, Product initial, Runnable onDone) {
        new StockDialog(operation, initial, onDone).montrer();
    }

    private void montrer() {
        List<Product> actifs = produits.allActive();
        if (actifs.isEmpty()) {
            ToastService.get().warning("Stock", "Aucun produit actif à mouvementer.");
            return;
        }

        cProduit = new ComboBox<>();
        cProduit.getStyleClass().add("field");
        cProduit.setMaxWidth(Double.MAX_VALUE);
        cProduit.getItems().setAll(actifs);
        cProduit.setValue(choisir(actifs));
        FormField fProduit = new FormField("Produit", cProduit, cProduit);

        contexte = new Label();
        contexte.getStyleClass().add("muted");

        boolean ajustement = operation == Operation.AJUSTEMENT;
        fQuantite = Fields.number(ajustement ? "Nouvelle quantité" : "Quantité", "0");
        fCout = Fields.number("Coût unitaire (facultatif)", "0");
        fMotif = Fields.text("Motif", motifRequis() ? "obligatoire" : "facultatif");

        if (!ajustement && operation != Operation.ENTREE) {
            fCout.setVisible(false);
            fCout.setManaged(false);
        }

        erreur = new Label();
        erreur.getStyleClass().add("field-error-msg");
        erreur.setWrapText(true);
        erreur.setManaged(false);
        erreur.setVisible(false);

        VBox corps = new VBox(12, fProduit, contexte, fQuantite, fCout, fMotif, erreur);
        corps.setPrefWidth(400);

        Button annuler = Buttons.secondary("Annuler");
        Button valider = Buttons.primary(operation.action, operation.icone);
        HBox pied = new HBox(8, annuler, valider);
        pied.getStyleClass().add("modal-footer");

        VBox carte = DialogService.get().card(operation.titre, operation.icone, corps, pied);
        DialogService.ModalHandle handle = DialogService.get().custom(carte, true);

        annuler.setOnAction(e -> handle.close());
        valider.setOnAction(e -> {
            try {
                valider();
                handle.close();
                ToastService.get().success("Stock", operation.action + " enregistrée.");
                if (onDone != null) {
                    onDone.run();
                }
            } catch (ValidationException ex) {
                signaler(ex);
            } catch (RuntimeException ex) {
                ToastService.get().error("Stock", ex.getMessage());
            }
        });

        cProduit.valueProperty().addListener((o, a, b) -> majContexte());
        majContexte();
    }

    private void valider() {
        if (cProduit.getValue() == null) {
            throw new ValidationException("Sélectionnez un produit.");
        }
        int quantite = entier(fQuantite.textValue());
        String motif = fMotif.textValue();
        int id = cProduit.getValue().getId();
        switch (operation) {
            case ENTREE -> {
                if (quantite <= 0) {
                    throw new ValidationException("quantite", "La quantité doit être supérieure à zéro.");
                }
                stock.receive(id, quantite, montant(fCout.textValue()), vide(motif) ? null : motif.trim());
            }
            case SORTIE -> {
                if (quantite <= 0) {
                    throw new ValidationException("quantite", "La quantité doit être supérieure à zéro.");
                }
                stock.issue(id, quantite, motif);
            }
            case PERTE -> {
                if (quantite <= 0) {
                    throw new ValidationException("quantite", "La quantité doit être supérieure à zéro.");
                }
                stock.loss(id, quantite, motif);
            }
            case RETOUR -> {
                if (quantite <= 0) {
                    throw new ValidationException("quantite", "La quantité doit être supérieure à zéro.");
                }
                stock.returnStock(id, quantite, motif);
            }
            case AJUSTEMENT -> stock.adjust(id, quantite, motif);
        }
    }

    private void signaler(ValidationException ex) {
        FormField champ = switch (ex.getChamp() == null ? "" : ex.getChamp()) {
            case "quantite" -> fQuantite;
            default -> null;
        };
        if (champ != null) {
            champ.setError(ex.getMessage());
        } else {
            fMotif.setError(ex.getMessage());
        }
        erreur.setText(ex.getMessage());
        erreur.setManaged(true);
        erreur.setVisible(true);
    }

    private void majContexte() {
        Product p = cProduit.getValue();
        if (p == null) {
            contexte.setText("");
            return;
        }
        contexte.setText("Stock actuel : " + p.getQuantity() + "  •  " + p.getRef());
    }

    private Product choisir(List<Product> actifs) {
        if (initial != null) {
            for (Product p : actifs) {
                if (p.getId() == initial.getId()) {
                    return p;
                }
            }
        }
        return actifs.get(0);
    }

    private boolean motifRequis() {
        return operation == Operation.SORTIE || operation == Operation.PERTE
                || operation == Operation.RETOUR || operation == Operation.AJUSTEMENT;
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }

    private static int entier(String v) {
        if (vide(v)) {
            return 0;
        }
        try {
            return (int) Math.round(Double.parseDouble(v.replace(',', '.').trim()));
        } catch (NumberFormatException e) {
            throw new ValidationException("quantite", "La quantité n'est pas un nombre valide.");
        }
    }

    private static BigDecimal montant(String v) {
        if (vide(v)) {
            return null;
        }
        try {
            return new BigDecimal(v.replace(',', '.').replace(" ", "").trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Le coût unitaire n'est pas un nombre valide.");
        }
    }
}
