package com.techshop.controller;

import com.techshop.model.Denomination;
import com.techshop.service.CashService;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.ui.components.ToastService;
import com.techshop.util.Format;
import com.techshop.util.Numbers;
import com.techshop.util.ValidationException;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Clôture d'une session de caisse (SPEC §4.10) : comptage par coupures
 * (billets et pièces en DA), calcul en direct de l'écart compté − attendu
 * (coloré) et note de clôture.
 */
public final class CashCloseDialog {

    private CashCloseDialog() {
    }

    public static void ouvrir(BigDecimal attendu, Runnable apres) {
        Map<Denomination, TextField> champs = new LinkedHashMap<>();
        GridPane grille = new GridPane();
        grille.setHgap(12);
        grille.setVgap(8);
        grille.getStyleClass().add("cash-count-grid");

        int row = 0;
        Label titrePieces = null;
        for (Denomination d : Denomination.DZD) {
            if (!d.billet() && titrePieces == null) {
                titrePieces = new Label("PIÈCES");
                titrePieces.getStyleClass().add("field-label");
                grille.add(titrePieces, 0, row++, 3, 1);
            }
            Label libelle = new Label(d.label());
            libelle.getStyleClass().add("cash-denom");
            TextField champ = new TextField("0");
            champ.getStyleClass().add("field");
            champ.setPrefWidth(70);
            champ.setAlignment(Pos.CENTER_RIGHT);
            Label sousTotal = new Label(Format.money(BigDecimal.ZERO));
            sousTotal.getStyleClass().add("cash-denom-total");
            grille.add(libelle, 0, row);
            grille.add(new Label("×"), 1, row);
            grille.add(champ, 2, row);
            grille.add(sousTotal, 3, row);
            champs.put(d, champ);
            champ.textProperty().addListener((o, a, b) -> {
                try {
                    int n = Numbers.compte(b);
                    sousTotal.setText(Format.money(d.value().multiply(BigDecimal.valueOf(n))));
                } catch (RuntimeException e) {
                    sousTotal.setText(Format.money(BigDecimal.ZERO));
                }
                majTotaux(champs, attendu, null, null);
            });
            row++;
        }

        Label totalCompte = new Label(Format.money(BigDecimal.ZERO));
        totalCompte.getStyleClass().add("cash-total-value");
        Label attenduLabel = new Label(Format.money(attendu));
        attenduLabel.getStyleClass().add("cash-total-value");
        Label ecart = new Label(Format.money(BigDecimal.ZERO));
        ecart.getStyleClass().addAll("cash-ecart", "cash-ecart-ok");

        GridPane resume = new GridPane();
        resume.setHgap(16);
        resume.setVgap(6);
        resume.getStyleClass().add("cash-close-summary");
        resume.add(libelle("Attendu"), 0, 0);
        resume.add(attenduLabel, 1, 0);
        resume.add(libelle("Compté"), 0, 1);
        resume.add(totalCompte, 1, 1);
        resume.add(libelle("Écart"), 0, 2);
        resume.add(ecart, 1, 2);

        FormField note = Fields.area("Note de clôture (facultatif)",
                "Ex. écart expliqué, incident de caisse…");

        VBox corps = new VBox(12, resume, grille, note);
        corps.setPrefWidth(460);
        corps.setMaxHeight(520);

        Button annuler = Buttons.secondary("Annuler");
        Button cloturer = Buttons.primary("Clôturer la caisse", "lock");
        HBox pied = new HBox(8, annuler, cloturer);
        pied.getStyleClass().add("modal-footer");

        VBox carte = DialogService.get().card("Clôture de caisse — comptage", "briefcase", corps, pied);
        DialogService.ModalHandle handle = DialogService.get().custom(carte, true);

        annuler.setOnAction(e -> handle.close());
        cloturer.setOnAction(e -> {
            try {
                BigDecimal compte = total(champs);
                CashService.get().closeSession(compte, note.textValue());
                ToastService.get().success("Caisse", "Session clôturée — compté " + Format.money(compte));
                handle.close();
                if (apres != null) {
                    apres.run();
                }
            } catch (ValidationException ex) {
                ToastService.get().error("Caisse", ex.getMessage());
            }
        });

        majTotaux(champs, attendu, totalCompte, ecart);
    }

    private static void majTotaux(Map<Denomination, TextField> champs, BigDecimal attendu,
                                  Label totalCompte, Label ecart) {
        BigDecimal compte = total(champs);
        if (totalCompte != null) {
            totalCompte.setText(Format.money(compte));
        }
        if (ecart != null) {
            BigDecimal diff = compte.subtract(attendu);
            ecart.setText(Format.money(diff));
            ecart.getStyleClass().removeAll("cash-ecart-ok", "cash-ecart-moins", "cash-ecart-plus");
            if (diff.signum() == 0) {
                ecart.getStyleClass().add("cash-ecart-ok");
            } else if (diff.signum() > 0) {
                ecart.getStyleClass().add("cash-ecart-plus");
            } else {
                ecart.getStyleClass().add("cash-ecart-moins");
            }
        }
    }

    private static BigDecimal total(Map<Denomination, TextField> champs) {
        List<BigDecimal> total = new ArrayList<>();
        total.add(BigDecimal.ZERO);
        champs.forEach((d, champ) -> {
            int n = Numbers.compte(champ.getText());
            total.set(0, total.get(0).add(d.value().multiply(BigDecimal.valueOf(n))));
        });
        return total.get(0);
    }

    private static Label libelle(String texte) {
        Label l = new Label(texte.toUpperCase());
        l.getStyleClass().add("field-label");
        return l;
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }
}
