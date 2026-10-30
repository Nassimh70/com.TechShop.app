package com.techshop.controller;

import com.techshop.model.Customer;
import com.techshop.model.CustomerPayment;
import com.techshop.model.PaymentMethod;
import com.techshop.service.CustomerPaymentService;
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
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Règlement de dette client (SPEC §4.7) : montant (partiel ou total), date et
 * heure modifiables, mode de paiement et note. Le solde du client est diminué
 * à l'enregistrement. Permet aussi de corriger la date d'un règlement existant.
 */
public final class DebtPaymentDialog {

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter HEURE_AFFICHAGE = DateTimeFormatter.ofPattern("HH:mm");

    private DebtPaymentDialog() {
    }

    /** Nouveau règlement (partiel ou total) de la dette du client. */
    public static void ouvrir(Customer client, Runnable onDone) {
        Customer c = client;
        BigDecimal dette = c.getBalance();

        Label nom = new Label(c.getFullName() + "  ·  " + c.getCode());
        nom.getStyleClass().add("field-label");
        Label soldeActuel = new Label("Dette actuelle : " + Format.money(dette));
        soldeActuel.getStyleClass().add("muted");
        VBox entete = new VBox(4, nom, soldeActuel);
        entete.getStyleClass().add("card-inset");

        FormField montant = Fields.number("Montant encaissé (DA)", "Ex. 500 pour une tranche");
        Label aideMontant = new Label("Saisissez uniquement la somme versée par le client. "
                + "Pour régler la totalité de la dette, utilisez « Régler toute la dette ».");
        aideMontant.getStyleClass().add("muted");
        aideMontant.setWrapText(true);

        Button solder = Buttons.secondary("Régler toute la dette", "check-circle");
        solder.setOnAction(e -> {
            montant.clearError();
            montant.setValue(Format.moneyShort(dette));
        });
        HBox actionsMontant = new HBox(solder);
        actionsMontant.setAlignment(Pos.CENTER_RIGHT);
        actionsMontant.setMaxWidth(Double.MAX_VALUE);
        VBox montantBox = new VBox(6, montant, aideMontant, actionsMontant);

        DatePicker date = new DatePicker(LocalDate.now());
        date.getStyleClass().add("field");
        date.setMaxWidth(Double.MAX_VALUE);
        TextField heure = new TextField(LocalTime.now().format(HEURE_AFFICHAGE));
        heure.getStyleClass().add("field");
        heure.setPrefColumnCount(6);
        heure.setMaxWidth(110);
        Label dateErreur = messageErreur();

        VBox dateBox = new VBox(6, etiquette("Date"), date);
        VBox heureBox = new VBox(6, etiquette("Heure (HH:mm)"), heure);
        HBox.setHgrow(dateBox, Priority.ALWAYS);
        VBox quand = new VBox(6, new HBox(10, dateBox, heureBox), dateErreur);

        ComboBox<String> mode = new ComboBox<>();
        mode.getStyleClass().add("field");
        mode.setMaxWidth(Double.MAX_VALUE);
        mode.getItems().setAll(PaymentMethod.ESPECES.getLabel(),
                PaymentMethod.CARTE.getLabel(), PaymentMethod.VIREMENT.getLabel());
        mode.getSelectionModel().selectFirst();
        VBox modeBox = new VBox(6, etiquette("Mode de paiement"), mode);

        FormField note = Fields.text("Note (facultatif)", "Ex. versement partiel");

        Label aideDate = new Label("Date et heure réelles du versement : vous pouvez "
                + "les modifier, y compris pour un règlement plus ancien.");
        aideDate.getStyleClass().add("muted");
        aideDate.setWrapText(true);

        Label reste = new Label();
        reste.getStyleClass().add("muted");
        Runnable majReste = () -> {
            try {
                BigDecimal valeur = Numbers.money(montant.textValue());
                BigDecimal apres = dette.subtract(valeur);
                if (apres.signum() < 0) {
                    apres = BigDecimal.ZERO;
                }
                reste.setText("Dette restante après ce versement : " + Format.money(apres));
            } catch (RuntimeException ex) {
                reste.setText("Dette restante après ce versement : -");
            }
        };
        ((TextField) montant.styledControl()).textProperty().addListener((o, a, b) -> majReste.run());
        majReste.run();

        VBox corps = new VBox(12, entete, montantBox, quand, aideDate, modeBox, note, reste);
        corps.setPrefWidth(460);

        Button annuler = Buttons.secondary("Annuler");
        Button valider = Buttons.primary("Encaisser", "credit-card");
        HBox pied = new HBox(8, annuler, valider);
        pied.getStyleClass().add("modal-footer");

        VBox carte = DialogService.get().card("Règlement de dette", "credit-card", corps, pied);
        DialogService.ModalHandle handle = DialogService.get().custom(carte, false);
        javafx.application.Platform.runLater(() -> montant.styledControl().requestFocus());

        annuler.setOnAction(e -> handle.close());
        valider.setOnAction(e -> {
            montant.clearError();
            masquer(dateErreur);
            LocalDateTime moment = moment(date, heure, dateErreur);
            if (moment == null) {
                return;
            }
            BigDecimal valeur;
            try {
                valeur = Numbers.money(montant.textValue());
            } catch (RuntimeException ex) {
                montant.setError(ex.getMessage());
                return;
            }
            if (valeur.signum() <= 0) {
                montant.setError("Le montant doit être supérieur à zéro.");
                return;
            }
            try {
                CustomerPaymentService.get().encaisser(c.getId(), valeur, moment,
                        codeMode(mode.getValue()), note.textValue());
                handle.close();
                ToastService.get().success("Règlement enregistré",
                        Format.money(valeur) + " encaissé pour " + c.getFullName());
                if (onDone != null) {
                    onDone.run();
                }
            } catch (ValidationException ex) {
                montant.setError(ex.getMessage());
            }
        });
        valider.setDefaultButton(true);
    }

    /** Correction de la date (et de la note) d'un règlement déjà enregistré. */
    public static void modifier(CustomerPayment paiement, Runnable onDone) {
        LocalDateTime ancien = paiement.getPaymentDate();

        Label montant = new Label(Format.money(paiement.getAmount()));
        montant.getStyleClass().add("field-label");
        Label mode = new Label(PaymentMethod.libelle(paiement.getPaymentMethod().name()));
        mode.getStyleClass().add("muted");
        VBox entete = new VBox(4,
                ligne("Montant encaissé", montant),
                ligne("Mode", mode));
        entete.getStyleClass().add("card-inset");

        DatePicker date = new DatePicker(ancien.toLocalDate());
        date.getStyleClass().add("field");
        date.setMaxWidth(Double.MAX_VALUE);
        TextField heure = new TextField(ancien.toLocalTime().format(HEURE_AFFICHAGE));
        heure.getStyleClass().add("field");
        heure.setPrefColumnCount(6);
        heure.setMaxWidth(110);
        Label dateErreur = messageErreur();

        VBox dateBox = new VBox(6, etiquette("Date"), date);
        VBox heureBox = new VBox(6, etiquette("Heure (HH:mm)"), heure);
        HBox.setHgrow(dateBox, Priority.ALWAYS);
        VBox quand = new VBox(6, new HBox(10, dateBox, heureBox), dateErreur);

        FormField note = Fields.text("Note (facultatif)", "");
        note.setValue(paiement.getNote() == null ? "" : paiement.getNote());

        Label aide = new Label("Seules la date, l'heure et la note sont modifiées ; "
                + "le montant et le solde du client restent inchangés.");
        aide.getStyleClass().add("muted");
        aide.setWrapText(true);

        VBox corps = new VBox(12, entete, quand, note, aide);
        corps.setPrefWidth(460);

        Button annuler = Buttons.secondary("Annuler");
        Button enregistrer = Buttons.primary("Enregistrer", "save");
        HBox pied = new HBox(8, annuler, enregistrer);
        pied.getStyleClass().add("modal-footer");

        VBox carte = DialogService.get().card("Modifier la date du règlement",
                "calendar", corps, pied);
        DialogService.ModalHandle handle = DialogService.get().custom(carte, false);

        annuler.setOnAction(e -> handle.close());
        enregistrer.setOnAction(e -> {
            masquer(dateErreur);
            LocalDateTime moment = moment(date, heure, dateErreur);
            if (moment == null) {
                return;
            }
            try {
                CustomerPaymentService.get().modifierDate(paiement.getId(), moment,
                        note.textValue());
                handle.close();
                ToastService.get().success("Règlement modifié",
                        "Date enregistrée : " + Format.dateTime(moment));
                if (onDone != null) {
                    onDone.run();
                }
            } catch (ValidationException ex) {
                afficher(dateErreur, ex.getMessage());
            }
        });
        enregistrer.setDefaultButton(true);
    }

    // ------------------------------------------------------------------ helpers

    private static LocalDateTime moment(DatePicker date, TextField heure, Label erreur) {
        if (date.getValue() == null) {
            afficher(erreur, "La date est obligatoire.");
            return null;
        }
        String saisie = heure.getText() == null || heure.getText().isBlank()
                ? "00:00" : heure.getText().trim().replace('h', ':');
        try {
            LocalTime t = LocalTime.parse(saisie, HEURE);
            return LocalDateTime.of(date.getValue(), t);
        } catch (DateTimeParseException ex) {
            afficher(erreur, "Heure invalide : utilisez le format HH:mm (ex. 14:30).");
            return null;
        }
    }

    private static String codeMode(String label) {
        if (label != null) {
            String cible = Normalizer.normalize(label, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "");
            for (PaymentMethod m : PaymentMethod.values()) {
                String libelle = Normalizer.normalize(m.getLabel(), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "");
                if (m.name().equalsIgnoreCase(label) || libelle.equalsIgnoreCase(cible)) {
                    return m.name();
                }
            }
        }
        return PaymentMethod.ESPECES.name();
    }

    private static Label etiquette(String texte) {
        Label l = new Label(texte.toUpperCase());
        l.getStyleClass().add("field-label");
        return l;
    }

    private static Label messageErreur() {
        Label l = new Label();
        l.getStyleClass().add("field-error-msg");
        l.setWrapText(true);
        l.setManaged(false);
        l.setVisible(false);
        return l;
    }

    private static void afficher(Label erreur, String message) {
        erreur.setText(message);
        erreur.setManaged(true);
        erreur.setVisible(true);
    }

    private static void masquer(Label erreur) {
        erreur.setText(null);
        erreur.setManaged(false);
        erreur.setVisible(false);
    }

    private static HBox ligne(String libelle, Label valeur) {
        Label l = new Label(libelle);
        l.getStyleClass().add("muted");
        HBox box = new HBox(8, l, valeur);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
