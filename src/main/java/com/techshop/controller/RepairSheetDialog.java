package com.techshop.controller;

import com.techshop.model.NotificationLog;
import com.techshop.model.PaymentMethod;
import com.techshop.model.RepairAttachment;
import com.techshop.model.RepairPart;
import com.techshop.model.RepairPayment;
import com.techshop.model.RepairStatus;
import com.techshop.model.RepairStatusHistory;
import com.techshop.model.RepairTicket;
import com.techshop.model.Role;
import com.techshop.dao.UserDao;
import com.techshop.service.PermissionDefaults;
import com.techshop.service.PermissionService;
import com.techshop.service.RepairDocumentService;
import com.techshop.service.RepairService;
import com.techshop.ui.Icons;
import com.techshop.ui.components.Avatar;
import com.techshop.ui.components.Badge;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.EmptyState;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.ui.components.ToastService;
import com.techshop.util.Format;
import com.techshop.util.Numbers;
import com.techshop.util.ValidationException;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Fiche détaillée d'un dossier de réparation (SPEC §4.9) : en-tête éditable,
 * chronologie verticale, diagnostic, pièces (sortie de stock), main d'œuvre,
 * totaux, acomptes, notes internes et photos. Les actions (changer de statut,
 * notifier le client, imprimer le bon de retrait, facturer, remettre l'appareil)
 * passent toutes par {@link RepairService}.
 */
public final class RepairSheetDialog {

    private RepairSheetDialog() {
    }

    private static boolean nonVide(String s) {
        return s != null && !s.isBlank();
    }

    public static void ouvrir(RepairTicket ticket, Runnable onDone) {
        new Feuille(ticket, onDone).afficher();
    }

    private static final class Feuille {

        private final RepairService svc = RepairService.get();
        private final UserDao users = new UserDao();
        private final Runnable onDone;
        private final int id;

        private RepairTicket t;
        private DialogService.ModalHandle handle;
        private final VBox corps = new VBox(14);
        private int ongletActif = 0;

        Feuille(RepairTicket ticket, Runnable onDone) {
            this.id = ticket.getId();
            this.onDone = onDone;
        }

        void afficher() {
            corps.setPrefWidth(880);
            Button fermer = Buttons.secondary("Fermer");
            Region espace = new Region();
            HBox.setHgrow(espace, Priority.ALWAYS);
            HBox pied = new HBox(espace, fermer);
            pied.getStyleClass().add("modal-footer");

            VBox carte = DialogService.get().card("Dossier de réparation", "tool", corps, pied);
            carte.setPrefWidth(920);
            handle = DialogService.get().custom(carte, false);
            fermer.setOnAction(e -> fermer());
            maj();
        }

        private void fermer() {
            if (handle != null) {
                handle.close();
            }
            if (onDone != null) {
                onDone.run();
            }
        }

        private void rafraichir() {
            maj();
        }

        private void maj() {
            try {
                t = svc.byId(id);
            } catch (RuntimeException e) {
                ToastService.get().error("Dossier", e.getMessage());
                if (handle != null) {
                    handle.close();
                }
                return;
            }
            TabPane onglets = new TabPane();
            onglets.getStyleClass().add("repair-tabs");
            onglets.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            onglets.setPrefHeight(430);
            onglets.setMinHeight(430);
            Tab dossier = new Tab("Dossier", defile(ongletDossier()));
            Tab pieces = new Tab("Pièces & totaux", defile(ongletPieces()));
            Tab suivi = new Tab("Suivi", defile(ongletSuivi()));
            Tab photos = new Tab("Photos", defile(ongletPhotos()));
            onglets.getTabs().setAll(dossier, pieces, suivi, photos);
            onglets.getSelectionModel().select(Math.max(0, Math.min(ongletActif, 3)));
            onglets.getSelectionModel().selectedIndexProperty().addListener(
                    (o, a, b) -> ongletActif = b.intValue());
            VBox.setVgrow(onglets, Priority.ALWAYS);
            corps.getChildren().setAll(entete(), barreActions(), onglets);
        }

        // ---------------------------------------------------------- en-tête

        private Node entete() {
            Label numero = new Label(t.getNumber());
            numero.getStyleClass().add("sheet-number");
            Badge statut = badge(t.getStatus());
            Label client = new Label(t.customerLabel()
                    + (t.getCustomerPhone() == null || t.getCustomerPhone().isBlank()
                            ? "" : "  ·  " + t.getCustomerPhone()));
            client.getStyleClass().add("sheet-client");
            Label appareil = new Label(t.deviceLabel()
                    + (t.getSerialOrImei() == null || t.getSerialOrImei().isBlank()
                            ? "" : "  ·  IMEI " + t.getSerialOrImei()));
            appareil.getStyleClass().add("sheet-device");
            Label tech = new Label((t.getTechnicianName() == null ? "Non assigné" : t.getTechnicianName())
                    + (t.getPromisedDate() == null ? "" : "  ·  prévu le " + Format.date(t.getPromisedDate())));
            tech.getStyleClass().add("muted");

            VBox texte = new VBox(3, new HBox(10, numero, statut), client, appareil, tech);
            HBox.setHgrow(texte, Priority.ALWAYS);

            HBox stats = new HBox(18,
                    miniStat("Total", Format.money(t.getTotal())),
                    miniStat("Payé", Format.money(t.getPaidAmount())),
                    miniStat("Reste dû", Format.money(t.getDueAmount())));
            if (t.isLate()) {
                Badge retard = Badge.of("En retard de " + t.lateDays() + " j", Badge.BadgeType.DANGER);
                stats.getChildren().add(retard);
            }
            stats.setAlignment(Pos.CENTER_RIGHT);

            HBox boite = new HBox(12, texte, stats);
            boite.getStyleClass().add("sheet-header");
            boite.setAlignment(Pos.CENTER_LEFT);
            return boite;
        }

        private VBox miniStat(String libelle, String valeur) {
            Label l = new Label(libelle.toUpperCase());
            l.getStyleClass().add("stat-label");
            Label v = new Label(valeur);
            v.getStyleClass().add("sheet-stat");
            VBox b = new VBox(2, l, v);
            b.setAlignment(Pos.CENTER_RIGHT);
            return b;
        }

        // ---------------------------------------------------------- actions

        private Node barreActions() {
            FlowPane barre = new FlowPane(8, 8);
            barre.getStyleClass().add("sheet-actions");

            // Changer de statut (hors livraison : passe par la remise)
            if (PermissionService.get().can(PermissionDefaults.M_REPAIRS, PermissionService.Action.EDIT)
                    && !RepairStatus.isTerminal(t.getStatus())) {
                List<String> cibles = new ArrayList<>();
                ComboBox<String> cible = new ComboBox<>();
                cible.getStyleClass().add("field");
                cible.setPromptText("Nouveau statut…");
                for (String s : RepairStatus.allowedFrom(t.getStatus())) {
                    if (!RepairStatus.LIVREE.equals(s)) {
                        cibles.add(s);
                        cible.getItems().add(RepairStatus.label(s));
                    }
                }
                cible.setPrefWidth(180);
                Button changer = Buttons.secondary("Changer le statut", "shuffle");
                changer.setOnAction(e -> {
                    int i = cible.getSelectionModel().getSelectedIndex();
                    if (i < 0) {
                        ToastService.get().warning("Statut", "Choisissez un nouveau statut.");
                        return;
                    }
                    changeStatut(cibles.get(i));
                });
                barre.getChildren().addAll(cible, changer);
            }

            if (RepairStatus.PRETE.equals(t.getStatus())
                    && PermissionService.get().can(PermissionDefaults.M_REPAIRS,
                    PermissionService.Action.VALIDATE)) {
                Button remettre = Buttons.primary("Remettre l'appareil", "check-circle");
                remettre.setOnAction(e -> {
                    RepairDeliverDialog.ouvrir(t, () -> {
                        maj();
                        if (onDone != null) {
                            onDone.run();
                        }
                    });
                });
                barre.getChildren().add(remettre);
            }

            if (PermissionService.get().can(PermissionDefaults.M_REPAIRS, PermissionService.Action.EDIT)) {
                Button notifier = Buttons.outline("Notifier le client", "bell");
                notifier.setOnAction(e -> notifier());
                barre.getChildren().add(notifier);
            }

            Button bonRetrait = Buttons.outline("Bon de retrait", "printer");
            bonRetrait.setOnAction(e -> document(false));
            barre.getChildren().add(bonRetrait);

            Button facture = Buttons.outline("Facture", "file-text");
            facture.setOnAction(e -> document(true));
            barre.getChildren().add(facture);

            if (PermissionService.get().can(PermissionDefaults.M_REPAIRS, PermissionService.Action.EDIT)
                    && !RepairStatus.isTerminal(t.getStatus())) {
                Button annuler = Buttons.danger("Annuler le dossier", "x-circle");
                annuler.setOnAction(e -> annulerDossier());
                barre.getChildren().add(annuler);
            }
            return barre;
        }

        // ---------------------------------------------------------- onglet 1

        private Node ongletDossier() {
            FormField diagnostic = Fields.area("Diagnostic technique", "Panne identifiée, intervention à réaliser…");
            diagnostic.setValue(t.getDiagnosis() == null ? "" : t.getDiagnosis());
            FormField notes = Fields.area("Notes internes", "Informations réservées à l'atelier");
            notes.setValue(t.getInternalNotes() == null ? "" : t.getInternalNotes());

            boolean editable = PermissionService.get().can(PermissionDefaults.M_REPAIRS,
                    PermissionService.Action.EDIT)
                    && !RepairStatus.isTerminal(t.getStatus());

            VBox gauche = new VBox(14,
                    carteInfos("Client",
                            ligne("Nom", t.customerLabel()),
                            ligne("Téléphone", t.getCustomerPhone()),
                            ligne("Dossier", t.getNumber()),
                            ligne("Reçu le", Format.dateTime(t.getReceivedAt()))),
                    carteInfos("Appareil",
                            ligne("Type", t.getDeviceType()),
                            ligne("Marque / modèle", t.deviceLabel()),
                            ligne("IMEI / série", t.getSerialOrImei()),
                            ligne("Code déverrouillage", t.getDevicePassword()),
                            ligne("Accessoires laissés", t.getAccessories())),
                    carteInfos("Devis & garantie",
                            ligne("Devis estimé", Format.money(t.getQuotedAmount())),
                            ligne("Garantie", t.getWarrantyDays() + " jours"),
                            ligne("Technicien", t.getTechnicianName() == null ? "Non assigné" : t.getTechnicianName())));

            VBox droite = new VBox(14,
                    carteTexte("Panne déclarée par le client", t.getReportedIssue()),
                    diagnostic, notes);
            if (editable) {
                Button enregistrer = Buttons.primary("Enregistrer le diagnostic", "save");
                enregistrer.setOnAction(e -> {
                    try {
                        svc.updateDiagnosis(t.getId(), diagnostic.textValue(), notes.textValue());
                        ToastService.get().success("Dossier", "Diagnostic enregistré.");
                        rafraichir();
                    } catch (RuntimeException ex) {
                        ToastService.get().error("Dossier", ex.getMessage());
                    }
                });
                droite.getChildren().add(enregistrer);
            } else {
                diagnostic.setDisable(true);
                notes.setDisable(true);
            }
            if (nonVide(t.getPhysicalCondition())) {
                droite.getChildren().add(0, carteTexte("État à la réception", t.getPhysicalCondition()));
            }

            HBox deux = new HBox(16, gauche, droite);
            HBox.setHgrow(gauche, Priority.ALWAYS);
            HBox.setHgrow(droite, Priority.ALWAYS);
            gauche.setPrefWidth(0);
            droite.setPrefWidth(0);
            deux.setPrefWidth(840);
            return deux;
        }

        // ---------------------------------------------------------- onglet 2

        private Node ongletPieces() {
            boolean editable = PermissionService.get().can(PermissionDefaults.M_REPAIRS,
                    PermissionService.Action.EDIT)
                    && !RepairStatus.isTerminal(t.getStatus());

            ObservableList<RepairPart> pieces = FXCollections.observableArrayList(t.getParts());
            TableView<RepairPart> table = new TableView<>(pieces);
            table.getStyleClass().add("data-table");
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            table.setPrefHeight(170);
            table.setPlaceholder(EmptyState.of("package", "Aucune pièce",
                    "Aucune pièce n'a encore été consommée sur ce dossier."));

            TableColumn<RepairPart, String> cNom = new TableColumn<>("Désignation");
            cNom.setMinWidth(220);
            cNom.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                    d.getValue().productName() == null ? "Pièce #" + d.getValue().productId()
                            : d.getValue().productName()));
            TableColumn<RepairPart, String> cQte = new TableColumn<>("Qté");
            cQte.setMinWidth(60);
            cQte.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(String.valueOf(d.getValue().quantity())));
            TableColumn<RepairPart, String> cPu = new TableColumn<>("P.U.");
            cPu.setMinWidth(110);
            cPu.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().unitPrice())));
            TableColumn<RepairPart, String> cTotal = new TableColumn<>("Total");
            cTotal.setMinWidth(120);
            cTotal.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().total())));
            TableColumn<RepairPart, RepairPart> cAct = new TableColumn<>("");
            cAct.setMinWidth(60);
            cAct.setMaxWidth(70);
            cAct.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
            cAct.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(RepairPart p, boolean empty) {
                    super.updateItem(p, empty);
                    setText(null);
                    if (empty || p == null || !editable) {
                        setGraphic(null);
                        return;
                    }
                    Button retirer = Buttons.iconCompact("trash-2", "Retirer la pièce");
                    retirer.setOnAction(e -> {
                        try {
                            svc.removePart(p.id(), t.getId());
                            ToastService.get().success("Pièce", "Pièce retirée et remise en stock.");
                            rafraichir();
                        } catch (RuntimeException ex) {
                            ToastService.get().error("Pièce", ex.getMessage());
                        }
                    });
                    setGraphic(retirer);
                }
            });
            table.getColumns().setAll(cNom, cQte, cPu, cTotal, cAct);
            table.setPrefHeight(200);

            VBox colonnePieces = new VBox(8, table);
            if (editable) {
                Button ajouter = Buttons.primary("Ajouter une pièce", "plus");
                ajouter.setOnAction(e -> RepairPartPickerDialog.ouvrir(t.getId(), this::rafraichir));
                colonnePieces.getChildren().add(ajouter);
            }
            HBox.setHgrow(colonnePieces, Priority.ALWAYS);
            colonnePieces.setPrefWidth(0);

            FormField mo = Fields.number("Main d'œuvre", "0");
            mo.setValue(Format.moneyShort(t.getLaborCost()).replace("\u00A0", "").replace(" ", ""));
            FormField remise = Fields.number("Remise", "0");
            remise.setValue(Format.moneyShort(t.getDiscount()).replace("\u00A0", "").replace(" ", ""));
            Button maj = Buttons.secondary("Mettre à jour", "save");
            maj.setDisable(!editable);
            maj.setOnAction(e -> {
                try {
                    svc.updateLabor(t.getId(), Numbers.money(mo.textValue()), Numbers.money(remise.textValue()));
                    ToastService.get().success("Totaux", "Main d'œuvre et remise mises à jour.");
                    rafraichir();
                } catch (RuntimeException ex) {
                    ToastService.get().error("Totaux", ex.getMessage());
                }
            });
            HBox labor = new HBox(12, mo, remise, maj);
            labor.setAlignment(Pos.BOTTOM_LEFT);

            VBox totaux = new VBox(6,
                    ligne("Pièces", Format.money(t.getPartsTotal())),
                    ligne("Main d'œuvre", Format.money(t.getLaborCost())),
                    ligne("Remise", "-" + Format.money(t.getDiscount())),
                    ligneFort("TOTAL", Format.money(t.getTotal())),
                    ligne("Acomptes", Format.money(t.getPaidAmount())),
                    ligneFort("Reste dû", Format.money(t.getDueAmount())));
            totaux.getStyleClass().add("card-inset");
            totaux.setPrefWidth(280);

            HBox haut = new HBox(14, colonnePieces, totaux);
            haut.setAlignment(Pos.TOP_LEFT);

            return new VBox(16,
                    titreSection("Pièces utilisées (sortie de stock)"),
                    haut,
                    labor,
                    titreSection("Règlements"),
                    tableReglements(editable));
        }

        // ---------------------------------------------------------- onglet 3 & 4

        private Node ongletSuivi() {
            return new VBox(16, titreSection("Chronologie du dossier"), chronologie(),
                    titreSection("Notifications au client"), notifications());
        }

        private Node ongletPhotos() {
            boolean editable = PermissionService.get().can(PermissionDefaults.M_REPAIRS,
                    PermissionService.Action.EDIT);
            TilePane grille = new TilePane(10, 10);
            grille.setPrefColumns(4);
            if (t.getAttachments().isEmpty()) {
                grille.getChildren().add(new Label("Aucune photo pour ce dossier."));
            }
            for (RepairAttachment a : t.getAttachments()) {
                VBox carte = new VBox(6);
                carte.getStyleClass().add("photo-card");
                Label nom = new Label(Format.ellipsis(a.fileName(), 22));
                nom.getStyleClass().add("photo-name");
                Label meta = new Label(a.sizeBytes() == null ? "" : (a.sizeBytes() / 1024) + " Ko");
                meta.getStyleClass().add("muted");
                carte.getChildren().addAll(Icons.feather("image", 22), nom, meta);
                if (editable) {
                    Button sup = Buttons.ghostIcon("trash-2", "Supprimer");
                    sup.setOnAction(e -> {
                        svc.removeAttachment(a.id(), t.getId());
                        ToastService.get().success("Photo", "Pièce jointe supprimée.");
                        rafraichir();
                    });
                    carte.getChildren().add(sup);
                }
                grille.getChildren().add(carte);
            }
            VBox box = new VBox(12, titreSection("Photos & pièces jointes"), grille);
            if (editable) {
                Button ajouter = Buttons.primary("Ajouter une photo", "upload");
                ajouter.setOnAction(e -> choisirPhoto());
                box.getChildren().add(ajouter);
            }
            return box;
        }

        // ------------------------------------------------------- composants

        private Node tableReglements(boolean editable) {
            TableView<RepairPayment> table = new TableView<>(
                    FXCollections.observableArrayList(t.getPayments()));
            table.getStyleClass().add("data-table");
            table.setPrefHeight(150);
            table.setPlaceholder(EmptyState.of("credit-card", "Aucun règlement",
                    "Aucun acompte ni solde n'a encore été enregistré."));
            TableColumn<RepairPayment, String> cDate = new TableColumn<>("Date");
            cDate.setMinWidth(150);
            cDate.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.dateTime(d.getValue().paymentDate())));
            TableColumn<RepairPayment, String> cMode = new TableColumn<>("Mode");
            cMode.setMinWidth(110);
            cMode.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().methodLabel()));
            TableColumn<RepairPayment, String> cMontant = new TableColumn<>("Montant");
            cMontant.setMinWidth(120);
            cMontant.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().amount())));
            table.getColumns().setAll(cDate, cMode, cMontant);

            Button ajouter = Buttons.secondary("Ajouter un acompte", "plus");
            ajouter.setDisable(!editable);
            ajouter.setOnAction(e -> ajouterAcompte());
            VBox box = new VBox(8, table, ajouter);
            return box;
        }

        private Node chronologie() {
            VBox timeline = new VBox(0);
            if (t.getHistory().isEmpty()) {
                timeline.getChildren().add(new Label("Aucun événement."));
            }
            for (RepairStatusHistory h : t.getHistory()) {
                Region dot = new Region();
                dot.getStyleClass().add("timeline-dot");
                Region trait = new Region();
                trait.getStyleClass().add("timeline-line");
                VBox marqueur = new VBox(dot, trait);
                marqueur.setAlignment(Pos.TOP_CENTER);

                Label statut = new Label(h.newStatusLabel());
                statut.getStyleClass().add("timeline-status");
                Label meta = new Label(Format.dateTime(h.changedAt())
                        + (h.userName() == null ? "" : "  ·  " + h.userName()));
                meta.getStyleClass().add("muted");
                VBox contenu = new VBox(2, statut);
                if (h.comment() != null && !h.comment().isBlank()) {
                    Label c = new Label(h.comment());
                    c.setWrapText(true);
                    contenu.getChildren().add(c);
                }
                contenu.getChildren().add(meta);

                HBox ligne = new HBox(12, marqueur, contenu);
                ligne.getStyleClass().add("timeline-row");
                timeline.getChildren().add(ligne);
            }
            return timeline;
        }

        private Node notifications() {
            VBox liste = new VBox(8);
            if (t.getId() > 0) {
                List<NotificationLog> logs = svc.notifications(t.getId());
                if (logs.isEmpty()) {
                    liste.getChildren().add(new Label("Aucune notification envoyée."));
                }
                for (NotificationLog n : logs) {
                    Label texte = new Label(n.message());
                    texte.setWrapText(true);
                    Label meta = new Label(n.channelLabel() + "  ·  " + Format.dateTime(n.createdAt())
                            + "  ·  " + (n.recipient() == null ? "—" : n.recipient()));
                    meta.getStyleClass().add("muted");
                    VBox item = new VBox(2, texte, meta);
                    item.getStyleClass().add("card-inset");
                    liste.getChildren().add(item);
                }
            }
            return liste;
        }

        private VBox carteInfos(String titre, Node... lignes) {
            VBox box = new VBox(6);
            box.getStyleClass().add("card-inset");
            box.getChildren().add(titreSection(titre));
            box.getChildren().addAll(lignes);
            return box;
        }

        private VBox carteTexte(String titre, String texte) {
            Label contenu = new Label(texte == null || texte.isBlank() ? "—" : texte);
            contenu.setWrapText(true);
            VBox box = new VBox(6, titreSection(titre), contenu);
            box.getStyleClass().add("card-inset");
            return box;
        }

        private Label titreSection(String texte) {
            Label l = new Label(texte.toUpperCase());
            l.getStyleClass().add("card-title");
            return l;
        }

        private HBox ligne(String libelle, String valeur) {
            Label l = new Label(libelle);
            l.getStyleClass().add("muted");
            Label v = new Label(valeur == null || valeur.isBlank() ? "—" : valeur);
            v.setWrapText(true);
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            HBox box = new HBox(8, l, sp, v);
            box.setAlignment(Pos.CENTER_LEFT);
            return box;
        }

        private HBox ligneFort(String libelle, String valeur) {
            HBox box = ligne(libelle, valeur);
            box.getStyleClass().add("total-line");
            if (box.getChildren().size() >= 3) {
                box.getChildren().get(0).getStyleClass().add("total-label");
                box.getChildren().get(2).getStyleClass().add("total-value");
            }
            return box;
        }

        private ScrollPane defile(Node contenu) {
            ScrollPane sp = new ScrollPane(contenu);
            sp.setFitToWidth(true);
            sp.getStyleClass().add("dialog-scroll");
            sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            return sp;
        }

        // ----------------------------------------------------------- actions

        private void changeStatut(String code) {
            try {
                svc.changeStatus(t.getId(), code, "Mise à jour depuis la fiche");
                ToastService.get().success("Statut", "Dossier placé en « " + RepairStatus.label(code) + " ».");
                rafraichir();
                if (onDone != null) {
                    onDone.run();
                }
            } catch (ValidationException e) {
                ToastService.get().error("Statut", e.getMessage());
            } catch (RuntimeException e) {
                ToastService.get().error("Statut", e.getMessage());
            }
        }

        private void annulerDossier() {
            DialogService.get().confirm("Annuler le dossier",
                    "Confirmer l'annulation de " + t.getNumber()
                            + " ? Les pièces consommées seront remises en stock.",
                    "Annuler le dossier", true).thenAccept(ok -> {
                if (!ok) {
                    return;
                }
                try {
                    svc.changeStatus(t.getId(), RepairStatus.ANNULEE, "Annulation depuis la fiche");
                    ToastService.get().success("Dossier annulé", t.getNumber());
                    rafraichir();
                    if (onDone != null) {
                        onDone.run();
                    }
                } catch (RuntimeException ex) {
                    ToastService.get().error("Annulation", ex.getMessage());
                }
            });
        }

        private void notifier() {
            FormField canal = Fields.combo("Canal", List.of("SMS", "Appel", "Email"));
            canal.setValue("SMS");
            FormField message = Fields.area("Message", "Texte envoyé au client (facultatif)");
            message.setValue("Bonjour " + (t.getCustomerName() == null ? "" : t.getCustomerName())
                    + ", votre appareil (" + t.getNumber() + ") est "
                    + RepairStatus.label(t.getStatus()).toLowerCase() + ".");
            VBox bodyBox = new VBox(12, canal, message,
                    new Label("L'envoi est simulé et journalisé dans l'historique."));
            bodyBox.setPrefWidth(420);
            Button annuler = Buttons.secondary("Annuler");
            Button envoyer = Buttons.primary("Envoyer", "send");
            HBox pied = new HBox(8, annuler, envoyer);
            pied.getStyleClass().add("modal-footer");
            VBox carte = DialogService.get().card("Notifier le client", "bell", bodyBox, pied);
            DialogService.ModalHandle h = DialogService.get().custom(carte, false);
            annuler.setOnAction(e -> h.close());
            envoyer.setOnAction(e -> {
                try {
                    svc.notifier(t.getId(), canal.textValue(), message.textValue());
                    h.close();
                    ToastService.get().success("Notification envoyée", "Message journalisé.");
                    rafraichir();
                } catch (RuntimeException ex) {
                    ToastService.get().error("Notification", ex.getMessage());
                }
            });
            envoyer.setDefaultButton(true);
        }

        private void ajouterAcompte() {
            FormField methode = Fields.combo("Mode", List.of(PaymentMethod.ESPECES.getLabel(),
                    PaymentMethod.CARTE.getLabel(), PaymentMethod.VIREMENT.getLabel()));
            methode.setValue(PaymentMethod.ESPECES.getLabel());
            FormField montant = Fields.number("Montant", "0");
            montant.setValue("0");
            FormField note = Fields.text("Note (facultatif)", "Ex. acompte sur devis");
            VBox bodyBox = new VBox(12, methode, montant, note);
            bodyBox.setPrefWidth(380);
            Button annuler = Buttons.secondary("Annuler");
            Button valider = Buttons.primary("Encaisser", "credit-card");
            HBox pied = new HBox(8, annuler, valider);
            pied.getStyleClass().add("modal-footer");
            VBox carte = DialogService.get().card("Nouvel acompte", "credit-card", bodyBox, pied);
            DialogService.ModalHandle h = DialogService.get().custom(carte, false);
            annuler.setOnAction(e -> h.close());
            valider.setOnAction(e -> {
                try {
                    String code = PaymentMethod.ESPECES.name();
                    for (PaymentMethod m : PaymentMethod.values()) {
                        if (m.getLabel().equals(methode.textValue())) {
                            code = m.name();
                        }
                    }
                    svc.addDeposit(t.getId(), code, Numbers.money(montant.textValue()), note.textValue());
                    h.close();
                    ToastService.get().success("Acompte enregistré", Format.money(
                            Numbers.money(montant.textValue())));
                    rafraichir();
                } catch (ValidationException ex) {
                    ToastService.get().error("Acompte", ex.getMessage());
                } catch (RuntimeException ex) {
                    ToastService.get().error("Acompte", ex.getMessage());
                }
            });
            valider.setDefaultButton(true);
        }

        private void choisirPhoto() {
            FileChooser fc = new FileChooser();
            fc.setTitle("Ajouter une photo");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images",
                    "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
            File f = fc.showOpenDialog(corps.getScene() == null ? null : corps.getScene().getWindow());
            if (f == null) {
                return;
            }
            try {
                svc.addAttachment(t.getId(), f.toPath());
                ToastService.get().success("Photo ajoutée", f.getName());
                rafraichir();
            } catch (RuntimeException e) {
                ToastService.get().error("Photo", e.getMessage());
            }
        }

        private void document(boolean facture) {
            try {
                RepairTicket complet = svc.byId(t.getId());
                var pdf = facture ? RepairDocumentService.facture(complet)
                        : RepairDocumentService.bonRetrait(complet);
                try {
                    RepairDocumentService.imprimer(pdf);
                    ToastService.get().success("Document",
                            (facture ? "Facture" : "Bon de retrait") + " généré.");
                } catch (ValidationException ex) {
                    ToastService.get().info("Document",
                            "PDF enregistré : " + pdf.toAbsolutePath());
                }
            } catch (Exception e) {
                ToastService.get().error("Document", e.getMessage());
            }
        }

        // ------------------------------------------------------------ badges

        private static Badge badge(String statut) {
            return Badge.of(RepairStatus.label(statut), switch (statut) {
                case RepairStatus.PRETE -> Badge.BadgeType.SUCCESS;
                case RepairStatus.ATTENTE_PIECE -> Badge.BadgeType.WARNING;
                case RepairStatus.ANNULEE -> Badge.BadgeType.DANGER;
                case RepairStatus.RECU, RepairStatus.DIAGNOSTIC, RepairStatus.EN_REPARATION ->
                        Badge.BadgeType.INFO;
                default -> Badge.BadgeType.NEUTRAL;
            });
        }
    }
}
