package com.techshop.controller;

import com.techshop.model.Product;
import com.techshop.service.LabelService;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.ui.components.ToastService;
import com.techshop.util.ValidationException;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Génération et impression d'étiquettes code-barres (SPEC §4.5) pour une
 * sélection de produits. Le PDF est produit par {@link LabelService} puis
 * envoyé à l'imprimante système.
 */
public final class LabelDialog {

    private final List<Product> produits;

    private LabelDialog(List<Product> produits) {
        this.produits = produits;
    }

    public static void ouvrir(List<Product> produits) {
        new LabelDialog(produits).montrer();
    }

    private void montrer() {
        Label resume = new Label(produits.size() + " produit(s) sélectionné(s)");
        resume.getStyleClass().add("muted");

        FormField nombre = Fields.number("Étiquettes par produit", "1");
        nombre.setValue("1");

        CheckBox prix = new CheckBox("Imprimer le prix de vente");
        prix.setSelected(true);

        VBox corps = new VBox(12, resume, nombre, prix);
        corps.setPrefWidth(400);

        Button annuler = Buttons.secondary("Annuler");
        Button generer = Buttons.primary("Générer et imprimer", "printer");
        HBox pied = new HBox(8, annuler, generer);
        pied.getStyleClass().add("modal-footer");

        VBox carte = DialogService.get().card("Étiquettes code-barres", "printer", corps, pied);
        DialogService.ModalHandle handle = DialogService.get().custom(carte, true);

        annuler.setOnAction(e -> handle.close());
        generer.setOnAction(e -> {
            try {
                int copies = entier(nombre.textValue(), 1);
                Path pdf = LabelService.generer(produits, copies, prix.isSelected());
                LabelService.imprimer(pdf);
                ToastService.get().success("Étiquettes",
                        "Planche générée : " + pdf.getFileName());
                handle.close();
            } catch (ValidationException ex) {
                ToastService.get().error("Étiquettes", ex.getMessage());
            } catch (IOException ex) {
                ToastService.get().error("Étiquettes",
                        "Génération impossible : " + ex.getMessage());
            }
        });
    }

    private static int entier(String v, int defaut) {
        if (v == null || v.isBlank()) {
            return defaut;
        }
        try {
            return (int) Math.round(Double.parseDouble(v.replace(',', '.').trim()));
        } catch (NumberFormatException e) {
            throw new ValidationException("Le nombre d'étiquettes est invalide.");
        }
    }
}
