package com.techshop.controller;

import com.techshop.model.PaymentMethod;
import com.techshop.model.RepairTicket;
import com.techshop.service.RepairService;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;

/**
 * Dialogue de remise de l'appareil (SPEC §4.9) : solde réglé en espèces (encaissé
 * dans la session de caisse ouverte) ou mis en dette sur le compte du client.
 * Le dossier passe au statut « Livrée » et la garantie démarre.
 */
public final class RepairDeliverDialog {

    private RepairDeliverDialog() {
    }

    public static void ouvrir(RepairTicket ticket, Runnable onDone) {
        RepairService service = RepairService.get();
        RepairTicket t = service.byId(ticket.getId());

        Label solde = new Label(Format.money(t.getDueAmount()));
        solde.getStyleClass().add("deliver-amount");
        Label info = new Label(t.getNumber() + " — " + t.customerLabel() + " · " + t.deviceLabel());
        info.getStyleClass().add("muted");

        VBox recap = new VBox(4,
                info,
                ligne("Total réparé", Format.money(t.getTotal())),
                ligne("Déjà réglé", Format.money(t.getPaidAmount())),
                new HBox(8, new Label("Reste à régler"), new Region(), solde));
        recap.getStyleClass().add("card-inset");

        ComboBox<String> methode = new ComboBox<>();
        methode.getStyleClass().add("field");
        methode.getItems().setAll(PaymentMethod.ESPECES.getLabel(), PaymentMethod.CARTE.getLabel(),
                PaymentMethod.VIREMENT.getLabel(), PaymentMethod.CREDIT.getLabel() + " (mise en dette)");
        methode.getSelectionModel().selectFirst();
        methode.setMaxWidth(Double.MAX_VALUE);

        FormField montant = Fields.number("Montant encaissé",
                Format.moneyShort(t.getDueAmount()));
        montant.setValue(Format.moneyShort(t.getDueAmount()).replace("\u00A0", ""));
        FormField note = Fields.text("Note (facultatif)", "Ex. remise d'un étui offert");

        boolean dejaSolde = t.getDueAmount().signum() <= 0;
        if (dejaSolde) {
            montant.setValue("0");
        }

        VBox corps = new VBox(12, recap,
                new Label("Mode de règlement du solde"), methode, montant, note);
        corps.setPrefWidth(460);

        Button annuler = Buttons.secondary("Annuler");
        Button valider = Buttons.primary("Remettre l'appareil", "check");
        HBox pied = new HBox(8, annuler, valider);
        pied.getStyleClass().add("modal-footer");
        HBox.setHgrow(annuler, Priority.ALWAYS);
        annuler.setMaxWidth(Double.MAX_VALUE);

        VBox carte = DialogService.get().card("Remise de l'appareil", "package", corps, pied);
        DialogService.ModalHandle h = DialogService.get().custom(carte, false);
        annuler.setOnAction(e -> h.close());

        valider.setOnAction(ev -> {
            try {
                String code = methode.getSelectionModel().getSelectedIndex() == 3
                        ? PaymentMethod.CREDIT.name()
                        : switch (methode.getSelectionModel().getSelectedIndex()) {
                            case 1 -> PaymentMethod.CARTE.name();
                            case 2 -> PaymentMethod.VIREMENT.name();
                            default -> PaymentMethod.ESPECES.name();
                        };
                BigDecimal verse = dejaSolde ? BigDecimal.ZERO : Numbers.money(montant.textValue());
                service.deliver(t.getId(), code, verse, note.textValue());
                h.close();
                ToastService.get().success("Appareil remis",
                        t.getNumber() + " livré. Garantie : "
                                + (t.warrantyEnd() == null ? "—" : Format.date(t.warrantyEnd())));
                try {
                    RepairTicket complet = service.byId(t.getId());
                    com.techshop.service.RepairDocumentService.imprimer(
                            com.techshop.service.RepairDocumentService.bonRetrait(complet));
                } catch (Exception ex) {
                    ToastService.get().info("Bon de retrait",
                            "Généré dans le dossier data/retraits.");
                }
                if (onDone != null) {
                    onDone.run();
                }
            } catch (ValidationException e) {
                ToastService.get().error("Remise impossible", e.getMessage());
            } catch (RuntimeException e) {
                ToastService.get().error("Remise impossible", e.getMessage());
            }
        });
        valider.setDefaultButton(true);
    }

    private static HBox ligne(String libelle, String valeur) {
        Label l = new Label(libelle);
        l.getStyleClass().add("muted");
        Label v = new Label(valeur);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox box = new HBox(8, l, sp, v);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
