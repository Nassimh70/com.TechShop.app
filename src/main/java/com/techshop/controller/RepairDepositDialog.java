package com.techshop.controller;

import com.techshop.dao.UserDao;
import com.techshop.model.Customer;
import com.techshop.model.PaymentMethod;
import com.techshop.model.RepairTicket;
import com.techshop.model.Role;
import com.techshop.model.User;
import com.techshop.service.RepairDocumentService;
import com.techshop.service.RepairService;
import com.techshop.service.SessionContext;
import com.techshop.ui.Icons;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.ui.components.ToastService;
import com.techshop.util.Numbers;
import com.techshop.util.ValidationException;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialogue de dépôt d'un appareil en 4 étapes (SPEC §4.9) : client, appareil,
 * panne et devis, puis acompte et planification. Un bon de dépôt PDF (code-barres
 * du numéro de dossier) est généré à la fin.
 */
public final class RepairDepositDialog {

    private static final String[] ETAPES = {"Client", "Appareil", "Panne & devis", "Dépôt"};

    private RepairDepositDialog() {
    }

    /** Champs saisis, partagés entre les étapes. */
    private static final class Etat {
        final Customer[] client = new Customer[1];
        final FormField deviceType = Fields.combo("Type d'appareil",
                List.of("Téléphone", "Tablette", "PC portable", "Montre connectée", "Console", "Autre"));
        final FormField brand = Fields.text("Marque", "Ex. Samsung, Apple, Xiaomi…");
        final FormField modele = Fields.text("Modèle", "Ex. Galaxy A54, iPhone 15…");
        final FormField serie = Fields.text("IMEI / numéro de série", "Obligatoire pour un téléphone");
        final FormField code = Fields.text("Code de déverrouillage (facultatif)", "Ex. 1234");
        final FormField accessoires = Fields.text("Accessoires laissés", "Ex. coque, chargeur, carte SIM");
        final FormField etat = Fields.area("État à la réception", "Rayures, chocs, traces d'usure…");
        final FormField panne = Fields.area("Panne déclarée par le client", "Décrivez le problème constaté");
        final FormField diagnostic = Fields.area("Diagnostic initial (facultatif)", "");
        final FormField devis = Fields.number("Devis estimé", "0");
        final FormField garantie = Fields.number("Garantie (jours)", "90");
        final FormField acompte = Fields.number("Acompte versé", "0");
        final FormField notes = Fields.text("Notes internes (facultatif)", "");
        final DatePicker datePrevue = new DatePicker(LocalDate.now().plusDays(3));
        final ComboBox<String> methode = new ComboBox<>();
        final ComboBox<User> tech = new ComboBox<>();
        final Label clientLabel = new Label("Aucun client sélectionné");

        Etat() {
            deviceType.setValue("Téléphone");
            devis.setValue("0");
            garantie.setValue("90");
            acompte.setValue("0");
            datePrevue.getStyleClass().add("field");
            datePrevue.setMaxWidth(Double.MAX_VALUE);
            methode.getStyleClass().add("field");
            methode.getItems().setAll(PaymentMethod.ESPECES.getLabel(), PaymentMethod.CARTE.getLabel(),
                    PaymentMethod.VIREMENT.getLabel());
            methode.getSelectionModel().selectFirst();
            methode.setMaxWidth(Double.MAX_VALUE);
            tech.getStyleClass().add("field");
            tech.setMaxWidth(Double.MAX_VALUE);
            try {
                tech.getItems().setAll(new ArrayList<>(new UserDao().search(null, Role.TECHNICIEN, true)));
            } catch (Exception ignored) {
            }
            User moi = SessionContext.get().getUser();
            for (User u : tech.getItems()) {
                if (moi != null && u.getId() == moi.getId()) {
                    tech.getSelectionModel().select(u);
                }
            }
            if (tech.getSelectionModel().isEmpty() && !tech.getItems().isEmpty()) {
                tech.getSelectionModel().selectFirst();
            }
            clientLabel.getStyleClass().add("wizard-value");
        }

        String methodeSelectionnee() {
            String v = methode.getValue();
            if (v != null) {
                for (PaymentMethod m : PaymentMethod.values()) {
                    if (m.getLabel().equals(v)) {
                        return m.name();
                    }
                }
            }
            return PaymentMethod.ESPECES.name();
        }

        Integer technicien() {
            User u = tech.getValue();
            return u == null ? null : u.getId();
        }
    }

    public static void ouvrir(Runnable onDone) {
        Etat s = new Etat();
        int[] etape = {0};

        StackPane zone = new StackPane();
        zone.setAlignment(Pos.TOP_LEFT);
        ScrollPane scroll = new ScrollPane(zone);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(330);
        scroll.setMinHeight(330);

        HBox indicateur = new HBox(6);
        indicateur.setAlignment(Pos.CENTER_LEFT);

        Button precedent = Buttons.secondary("Précédent");
        Button suivant = Buttons.primary("Suivant", "arrow-right");
        Button annuler = Buttons.ghost("Annuler");
        Region espace = new Region();
        HBox.setHgrow(espace, Priority.ALWAYS);
        HBox pied = new HBox(8, annuler, espace, precedent, suivant);
        pied.getStyleClass().add("modal-footer");

        VBox corps = new VBox(14, indicateur, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        corps.setPrefWidth(640);

        VBox carte = DialogService.get().card("Nouveau dépôt en réparation", "tool", corps, pied);
        DialogService.ModalHandle handle = DialogService.get().custom(carte, false);

        Runnable rendre = () -> {
            int courante = etape[0];
            s.clientLabel.setText(s.client[0] == null ? "Aucun client sélectionné"
                    : s.client[0].getFullName() + "  ·  " + s.client[0].getCode()
                            + (s.client[0].getPhone() == null || s.client[0].getPhone().isBlank()
                                    ? "" : "  ·  " + s.client[0].getPhone()));
            zone.getChildren().setAll(contenu(courante, s));
            indicateur.getChildren().setAll(indicateurDe(courante).getChildren());
            precedent.setDisable(courante == 0);
            boolean fin = courante == ETAPES.length - 1;
            suivant.setText(fin ? "Créer le dossier" : "Suivant");
            suivant.setGraphic(Icons.feather(fin ? "check" : "arrow-right", 16));
        };

        rendre.run();

        annuler.setOnAction(e -> handle.close());
        precedent.setOnAction(e -> {
            if (etape[0] > 0) {
                etape[0]--;
                rendre.run();
            }
        });
        suivant.setOnAction(e -> {
            if (etape[0] == 0 && s.client[0] == null) {
                ToastService.get().warning("Client", "Sélectionnez ou créez un client.");
                return;
            }
            if (etape[0] < ETAPES.length - 1) {
                etape[0]++;
                rendre.run();
                return;
            }
            if (s.panne.textValue().isBlank()) {
                ToastService.get().warning("Panne", "La panne déclarée est obligatoire.");
                return;
            }
            try {
                RepairService.DepositRequest req = new RepairService.DepositRequest(
                        s.client[0].getId(), s.deviceType.textValue(), s.brand.textValue(),
                        s.modele.textValue(), s.serie.textValue(), s.code.textValue(),
                        s.accessoires.textValue(), s.etat.textValue(), s.panne.textValue(),
                        s.diagnostic.textValue(), Numbers.money(s.devis.textValue()),
                        s.methodeSelectionnee(), Numbers.money(s.acompte.textValue()),
                        s.datePrevue.getValue(), s.technicien(),
                        Numbers.entier(s.garantie.textValue(), 0), s.notes.textValue());
                RepairTicket t = RepairService.get().create(req);
                handle.close();
                ToastService.get().success("Dossier créé", t.getNumber() + " — " + t.deviceLabel());
                try {
                    RepairDocumentService.imprimer(RepairDocumentService.bonDepot(
                            RepairService.get().byId(t.getId())));
                } catch (Exception ex) {
                    ToastService.get().info("Bon de dépôt",
                            "Le PDF a été enregistré dans data/depots.");
                }
                if (onDone != null) {
                    onDone.run();
                }
            } catch (ValidationException ex) {
                ToastService.get().error("Dépôt", ex.getMessage());
            } catch (RuntimeException ex) {
                ToastService.get().error("Dépôt", ex.getMessage());
            }
        });
        suivant.setDefaultButton(true);
    }

    private static Node contenu(int etape, Etat s) {
        return switch (etape) {
            case 0 -> bloc("Étape 1 — Client",
                    "Le dossier est toujours rattaché à un client (création rapide possible).",
                    grilleClient(s));
            case 1 -> bloc("Étape 2 — Appareil",
                    "Identifiez l'appareil et son état à la réception.",
                    new VBox(12, s.deviceType, new HBox(12, s.brand, s.modele),
                            s.serie, new HBox(12, s.code, s.accessoires), s.etat));
            case 2 -> bloc("Étape 3 — Panne & devis",
                    "Décrivez la panne déclarée et estimez le devis communiqué au client.",
                    new VBox(12, s.panne, s.diagnostic, new HBox(12, s.devis, s.garantie)));
            default -> bloc("Étape 4 — Dépôt",
                    "Acompte éventuel, technicien assigné et date de remise prévue.",
                    new VBox(12, new HBox(12, s.acompte, s.methode), new HBox(12, s.tech, s.datePrevue),
                            s.notes));
        };
    }

    private static Node grilleClient(Etat s) {
        Button choisir = Buttons.outline("Choisir / créer un client", "users");
        choisir.setOnAction(e -> CustomerPickerDialog.ouvrir(c -> {
            if (c != null) {
                s.client[0] = c;
                s.clientLabel.setText(c.getFullName() + "  ·  " + c.getCode()
                        + (c.getPhone() == null || c.getPhone().isBlank() ? "" : "  ·  " + c.getPhone()));
            }
        }));
        VBox box = new VBox(10, new Label("Client du dossier"), choisir, s.clientLabel);
        box.getStyleClass().add("card-inset");
        return box;
    }

    private static VBox bloc(String titre, String sousTitre, Node contenu) {
        Label t = new Label(titre);
        t.getStyleClass().add("wizard-title");
        Label d = new Label(sousTitre);
        d.getStyleClass().add("muted");
        d.setWrapText(true);
        return new VBox(12, t, d, contenu);
    }

    private static HBox indicateurDe(int etape) {
        HBox box = new HBox(6);
        box.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < ETAPES.length; i++) {
            Label l = new Label((i + 1) + ". " + ETAPES[i]);
            l.getStyleClass().add("wizard-step");
            if (i == etape) {
                l.getStyleClass().add("active");
            } else if (i < etape) {
                l.getStyleClass().add("done");
            }
            box.getChildren().add(l);
            if (i < ETAPES.length - 1) {
                Label fleche = new Label("›");
                fleche.getStyleClass().add("wizard-arrow");
                box.getChildren().add(fleche);
            }
        }
        return box;
    }
}
