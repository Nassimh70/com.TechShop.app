package com.techshop.controller;

import com.techshop.service.CashService;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.ui.components.ToastService;
import com.techshop.util.Numbers;
import com.techshop.util.ValidationException;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Ouverture d'une session de caisse (SPEC §4.10) : saisie du fonds initial et
 * d'une note facultative.
 */
public final class CashOpenDialog {

    private CashOpenDialog() {
    }

    public static void ouvrir(Runnable apres) {
        FormField fonds = Fields.number("Fonds de caisse initial (DA)", "0,00");
        fonds.setValue("0");
        FormField note = Fields.area("Note (facultatif)", "Ex. fonds remis par la gérance");

        VBox corps = new VBox(12, fonds, note);
        corps.setPrefWidth(420);

        Button annuler = Buttons.secondary("Annuler");
        Button ouvrir = Buttons.primary("Ouvrir la caisse", "unlock");
        HBox pied = new HBox(8, annuler, ouvrir);
        pied.getStyleClass().add("modal-footer");

        VBox carte = DialogService.get().card("Ouvrir une session de caisse", "briefcase", corps, pied);
        DialogService.ModalHandle handle = DialogService.get().custom(carte, true);

        annuler.setOnAction(e -> handle.close());
        ouvrir.setOnAction(e -> {
            try {
                fonds.clearError();
                var session = CashService.get().openSession(Numbers.money(fonds.textValue()),
                        note.textValue());
                ToastService.get().success("Caisse",
                        "Session " + session.getNumber() + " ouverte.");
                handle.close();
                if (apres != null) {
                    apres.run();
                }
            } catch (ValidationException ex) {
                fonds.setError(ex.getMessage());
            }
        });
        ouvrir.setDefaultButton(true);
    }

    /** Message d'aide affiché dans certains écrans. */
    static Label aide(String texte) {
        Label l = new Label(texte);
        l.getStyleClass().add("muted");
        l.setWrapText(true);
        return l;
    }
}
