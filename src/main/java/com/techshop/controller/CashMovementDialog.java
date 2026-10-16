package com.techshop.controller;

import com.techshop.dao.ExpenseCategoryDao;
import com.techshop.model.CashMovementType;
import com.techshop.service.CashService;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.ui.components.ToastService;
import com.techshop.util.Numbers;
import com.techshop.util.ValidationException;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Ajout d'un mouvement de caisse (entrée / sortie d'espèces) avec motif et
 * catégorie de dépense (SPEC §4.10).
 */
public final class CashMovementDialog {

    private CashMovementDialog() {
    }

    public static void ouvrir(Runnable apres) {
        ComboBox<String> type = new ComboBox<>();
        type.getStyleClass().add("field");
        type.getItems().setAll("Entrée", "Sortie");
        type.getSelectionModel().selectFirst();
        type.setMaxWidth(Double.MAX_VALUE);

        FormField montant = Fields.number("Montant (DA)", "0,00");
        FormField motif = Fields.text("Motif", "Ex. achat fournitures, apport gérant…");

        ExpenseCategoryDao dao = new ExpenseCategoryDao();
        List<ExpenseCategoryDao.Option> options = new ArrayList<>();
        options.add(new ExpenseCategoryDao.Option(0, "— Aucune —"));
        try {
            options.addAll(dao.all());
        } catch (Exception ignored) {
            // base indisponible : la catégorie reste facultative
        }
        ComboBox<ExpenseCategoryDao.Option> categorie = new ComboBox<>();
        categorie.getStyleClass().add("field");
        categorie.getItems().setAll(options);
        categorie.getSelectionModel().selectFirst();
        categorie.setMaxWidth(Double.MAX_VALUE);
        VBox categorieBox = new VBox(6, libelle("Catégorie (sortie)"), categorie);

        VBox corps = new VBox(12, libelle("Type"), type, montant, motif, categorieBox);
        corps.setPrefWidth(420);

        Button annuler = Buttons.secondary("Annuler");
        Button valider = Buttons.primary("Enregistrer", "check");
        HBox pied = new HBox(8, annuler, valider);
        pied.getStyleClass().add("modal-footer");

        VBox carte = DialogService.get().card("Mouvement de caisse", "repeat", corps, pied);
        DialogService.ModalHandle handle = DialogService.get().custom(carte, true);

        annuler.setOnAction(e -> handle.close());
        valider.setOnAction(e -> {
            try {
                montant.clearError();
                CashMovementType t = type.getSelectionModel().getSelectedIndex() == 1
                        ? CashMovementType.SORTIE : CashMovementType.ENTREE;
                ExpenseCategoryDao.Option opt = categorie.getValue();
                Integer catId = opt == null || opt.id() == 0 ? null : opt.id();
                CashService.get().addMovement(t, Numbers.money(montant.textValue()),
                        motif.textValue(), catId);
                ToastService.get().success("Caisse", "Mouvement enregistré.");
                handle.close();
                if (apres != null) {
                    apres.run();
                }
            } catch (ValidationException ex) {
                montant.setError(ex.getMessage());
            }
        });
        valider.setDefaultButton(true);
    }

    private static javafx.scene.control.Label libelle(String texte) {
        javafx.scene.control.Label l = new javafx.scene.control.Label(texte.toUpperCase());
        l.getStyleClass().add("field-label");
        return l;
    }
}
