package com.techshop.controller;

import com.techshop.dao.CustomerDao;
import com.techshop.dao.CustomerPaymentDao;
import com.techshop.dao.SaleDao;
import com.techshop.model.Customer;
import com.techshop.model.CustomerPayment;
import com.techshop.model.PaymentMethod;
import com.techshop.model.RepairPayment;
import com.techshop.model.RepairTicket;
import com.techshop.model.Sale;
import com.techshop.service.PermissionDefaults;
import com.techshop.service.PermissionService;
import com.techshop.service.RepairService;
import com.techshop.service.SaleService;
import com.techshop.ui.components.Avatar;
import com.techshop.ui.components.Badge;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.EmptyState;
import com.techshop.util.Format;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Fiche client (SPEC §4.7) : coordonnées, historique des achats, dossiers de
 * réparation et paiements (règlements de dettes). Les règlements peuvent être
 * encaissés (partiel ou total) et leur date corrigée depuis cet écran.
 */
public final class CustomerDetailDialog {

    private CustomerDetailDialog() {
    }

    public static void ouvrir(Customer client, Runnable onDone) {
        Customer c = client;
        boolean peutEncaisser = PermissionService.get()
                .can(PermissionDefaults.M_CUSTOMERS, PermissionService.Action.EDIT);

        Label nom = new Label(c.getFullName());
        nom.getStyleClass().add("sheet-client");
        Label sousTitre = new Label(c.getCode()
                + (c.getPhone() == null || c.getPhone().isBlank() ? "" : "  ·  " + c.getPhone())
                + "  ·  solde " + Format.money(c.getBalance()));
        sousTitre.getStyleClass().add("muted");
        HBox tete = new HBox(10, Avatar.of(c.getFullName(), 34), new VBox(3, nom, sousTitre));
        tete.setAlignment(Pos.CENTER_LEFT);

        DialogService.ModalHandle[] handleRef = new DialogService.ModalHandle[1];
        Runnable rafraichir = () -> {
            if (handleRef[0] != null) {
                handleRef[0].close();
            }
            rouvrir(c.getId(), onDone);
        };

        TabPane onglets = new TabPane();
        onglets.getStyleClass().add("repair-tabs");
        onglets.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        onglets.setPrefHeight(400);
        onglets.setMinHeight(400);
        onglets.getTabs().setAll(
                new Tab("Coordonnées", defile(coordonnees(c))),
                new Tab("Achats", defile(achats(c))),
                new Tab("Réparations", defile(reparations(c))),
                new Tab("Paiements", defile(paiements(c, peutEncaisser, rafraichir))));

        VBox corps = new VBox(12, tete, onglets);
        corps.setPrefWidth(760);

        boolean avecDette = c.getBalance().signum() > 0;
        Button regler = Buttons.primary("Encaisser un règlement", "credit-card");
        regler.setVisible(peutEncaisser && avecDette);
        regler.setManaged(peutEncaisser && avecDette);
        regler.setOnAction(e -> DebtPaymentDialog.ouvrir(c, rafraichir));

        Button fermer = Buttons.secondary("Fermer");
        Region espace = new Region();
        HBox.setHgrow(espace, Priority.ALWAYS);
        HBox pied = new HBox(8, espace, regler, fermer);
        pied.getStyleClass().add("modal-footer");

        VBox carte = DialogService.get().card("Fiche client", "user", corps, pied);
        carte.setPrefWidth(800);
        handleRef[0] = DialogService.get().custom(carte, false);
        fermer.setOnAction(e -> {
            handleRef[0].close();
            if (onDone != null) {
                onDone.run();
            }
        });
    }

    /** Recharge la fiche après un règlement (solde et historique à jour). */
    private static void rouvrir(int customerId, Runnable onDone) {
        try {
            new CustomerDao().byId(customerId).ifPresent(c -> ouvrir(c, onDone));
        } catch (SQLException ignored) {
            // fiche laissée telle quelle si la base est indisponible
        }
    }

    // ------------------------------------------------------------- coordonnées

    private static Node coordonnees(Customer c) {
        VBox box = new VBox(8);
        box.getStyleClass().add("card-inset");
        box.getChildren().addAll(
                ligne("Code client", c.getCode()),
                ligne("Nom complet", c.getFullName()),
                ligne("Téléphone", c.getPhone()),
                ligne("Téléphone 2", c.getPhone2()),
                ligne("Email", c.getEmail()),
                ligne("Adresse", c.getAddress()),
                ligne("Solde (dette)", Format.money(c.getBalance())),
                ligne("Créé le", Format.dateTime(c.getCreatedAt())),
                ligne("Statut", c.isActive() ? "Actif" : "Inactif"),
                ligne("Notes", c.getNotes()));
        return box;
    }

    // ------------------------------------------------------------------ achats

    private static Node achats(Customer c) {
        ObservableList<Sale> ventes = FXCollections.observableArrayList();
        try {
            ventes.setAll(SaleService.get()
                    .history(new SaleDao.Filter(null, null, null, c.getId(), null, null, null), 0, 200)
                    .items());
        } catch (RuntimeException ignored) {
            // base indisponible : liste vide
        }

        TableView<Sale> table = new TableView<>(ventes);
        table.getStyleClass().add("data-table");
        table.setPlaceholder(EmptyState.of("shopping-bag", "Aucun achat",
                "Ce client n'a encore rien acheté."));

        TableColumn<Sale, String> cNum = new TableColumn<>("N°");
        cNum.setMinWidth(100);
        cNum.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getNumber()));
        TableColumn<Sale, String> cDate = new TableColumn<>("Date");
        cDate.setMinWidth(150);
        cDate.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                Format.dateTime(d.getValue().getSaleDate())));
        TableColumn<Sale, String> cTotal = new TableColumn<>("Total");
        cTotal.setMinWidth(120);
        cTotal.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().getTotal())));
        TableColumn<Sale, String> cDue = new TableColumn<>("Reste dû");
        cDue.setMinWidth(120);
        cDue.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().getDueAmount())));
        TableColumn<Sale, String> cStatut = new TableColumn<>("Règlement");
        cStatut.setMinWidth(130);
        cStatut.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().paymentStatusLabel()));
        table.getColumns().setAll(cNum, cDate, cTotal, cDue, cStatut);

        BigDecimal total = ventes.stream().map(Sale::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal du = ventes.stream().map(Sale::getDueAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        VBox cumul = new VBox(2,
                ligne("Nombre d'achats", String.valueOf(ventes.size())),
                ligne("Total acheté", Format.money(total)),
                ligne("Reste dû cumulé", Format.money(du)));
        cumul.getStyleClass().add("card-inset");
        return new VBox(12, table, cumul);
    }

    // ------------------------------------------------------------ réparations

    private static Node reparations(Customer c) {
        ObservableList<RepairTicket> tickets =
                FXCollections.observableArrayList(RepairService.get().byCustomer(c.getId()));

        TableView<RepairTicket> table = new TableView<>(tickets);
        table.getStyleClass().add("data-table");
        table.setPlaceholder(EmptyState.of("tool", "Aucune réparation",
                "Ce client n'a déposé aucun appareil au SAV."));

        TableColumn<RepairTicket, String> cNum = new TableColumn<>("N°");
        cNum.setMinWidth(100);
        cNum.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getNumber()));
        TableColumn<RepairTicket, String> cApp = new TableColumn<>("Appareil");
        cApp.setMinWidth(160);
        cApp.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().deviceLabel()));
        TableColumn<RepairTicket, String> cPanne = new TableColumn<>("Panne déclarée");
        cPanne.setMinWidth(190);
        cPanne.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getReportedIssue() == null ? "—" : d.getValue().getReportedIssue()));
        TableColumn<RepairTicket, RepairTicket> cStatut = new TableColumn<>("Statut");
        cStatut.setMinWidth(140);
        cStatut.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cStatut.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(RepairTicket t, boolean empty) {
                super.updateItem(t, empty);
                setText(null);
                setGraphic(empty || t == null ? null
                        : Badge.of(t.statusLabel(), badgeType(t.getStatus())));
            }
        });
        TableColumn<RepairTicket, String> cTotal = new TableColumn<>("Total");
        cTotal.setMinWidth(110);
        cTotal.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().getTotal())));
        TableColumn<RepairTicket, String> cDue = new TableColumn<>("Reste dû");
        cDue.setMinWidth(110);
        cDue.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().getDueAmount())));
        table.getColumns().setAll(cNum, cApp, cPanne, cStatut, cTotal, cDue);
        table.setRowFactory(tv -> {
            TableRow<RepairTicket> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    RepairSheetDialog.ouvrir(row.getItem(), null);
                }
            });
            return row;
        });

        Label aide = new Label("Double-cliquez sur un dossier pour ouvrir sa fiche complète.");
        aide.getStyleClass().add("muted");
        return new VBox(12, table, aide);
    }

    // -------------------------------------------------------------- paiements

    /** Ligne de règlement SAV (aplatie pour l'affichage). */
    private record ReglS(String dossier, LocalDateTime date, String mode, BigDecimal montant) {
    }

    private static Node paiements(Customer c, boolean peutEncaisser, Runnable rafraichir) {
        // 1) Règlements de dettes (customer_payments)
        List<CustomerPayment> dettes = new ArrayList<>();
        try {
            dettes.addAll(new CustomerPaymentDao().search(
                    new CustomerPaymentDao.Filter(c.getId(), null, null)));
        } catch (java.sql.SQLException ignored) {
            // liste vide
        }
        TableView<CustomerPayment> tableDettes = new TableView<>(
                FXCollections.observableArrayList(dettes));
        tableDettes.getStyleClass().add("data-table");
        tableDettes.setPrefHeight(150);
        tableDettes.setPlaceholder(EmptyState.of("credit-card", "Aucun règlement",
                "Aucun règlement de dette enregistré."));
        TableColumn<CustomerPayment, String> dDate = new TableColumn<>("Date");
        dDate.setMinWidth(150);
        dDate.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                Format.dateTime(d.getValue().getPaymentDate())));
        TableColumn<CustomerPayment, String> dMode = new TableColumn<>("Mode");
        dMode.setMinWidth(100);
        dMode.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getPaymentMethod() == null ? "—"
                        : PaymentMethod.libelle(d.getValue().getPaymentMethod().name())));
        TableColumn<CustomerPayment, String> dMontant = new TableColumn<>("Montant");
        dMontant.setMinWidth(120);
        dMontant.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().getAmount())));
        TableColumn<CustomerPayment, String> dNote = new TableColumn<>("Note");
        dNote.setMinWidth(180);
        dNote.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getNote() == null ? "—" : d.getValue().getNote()));
        TableColumn<CustomerPayment, String> dUser = new TableColumn<>("Encaissé par");
        dUser.setMinWidth(130);
        dUser.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getUsername() == null ? "—" : d.getValue().getUsername()));
        if (peutEncaisser) {
            TableColumn<CustomerPayment, CustomerPayment> dAct = new TableColumn<>("");
            dAct.setMinWidth(52);
            dAct.setMaxWidth(52);
            dAct.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
            dAct.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(CustomerPayment p, boolean empty) {
                    super.updateItem(p, empty);
                    setText(null);
                    setGraphic(null);
                    if (empty || p == null) {
                        return;
                    }
                    Button modif = Buttons.iconCompact("edit-2", "Modifier la date du règlement");
                    modif.setOnAction(e -> DebtPaymentDialog.modifier(p, rafraichir));
                    setGraphic(modif);
                }
            });
            tableDettes.getColumns().setAll(dDate, dMode, dMontant, dNote, dUser, dAct);
        } else {
            tableDettes.getColumns().setAll(dDate, dMode, dMontant, dNote, dUser);
        }

        // 2) Acomptes et soldes encaissés sur les dossiers SAV
        ObservableList<ReglS> reglSav = FXCollections.observableArrayList();
        BigDecimal totalSav = BigDecimal.ZERO;
        for (RepairTicket t : RepairService.get().byCustomer(c.getId())) {
            for (RepairPayment p : t.getPayments()) {
                reglSav.add(new ReglS(t.getNumber(), p.paymentDate(), p.methodLabel(), p.amount()));
                totalSav = totalSav.add(p.amount());
            }
        }
        TableView<ReglS> tableSav = new TableView<>(reglSav);
        tableSav.getStyleClass().add("data-table");
        tableSav.setPrefHeight(150);
        tableSav.setPlaceholder(EmptyState.of("tool", "Aucun acompte SAV",
                "Aucun acompte ni solde encaissé sur les dossiers SAV."));
        TableColumn<ReglS, String> sDossier = new TableColumn<>("Dossier");
        sDossier.setMinWidth(110);
        sDossier.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().dossier()));
        TableColumn<ReglS, String> sDate = new TableColumn<>("Date");
        sDate.setMinWidth(150);
        sDate.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                Format.dateTime(d.getValue().date())));
        TableColumn<ReglS, String> sMode = new TableColumn<>("Mode");
        sMode.setMinWidth(110);
        sMode.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().mode()));
        TableColumn<ReglS, String> sMontant = new TableColumn<>("Montant");
        sMontant.setMinWidth(120);
        sMontant.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                Format.money(d.getValue().montant())));
        tableSav.getColumns().setAll(sDossier, sDate, sMode, sMontant);

        BigDecimal totalRegle = dettes.stream().map(CustomerPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        VBox recap = new VBox(2,
                ligne("Solde dû actuel", Format.money(c.getBalance())),
                ligne("Total réglé sur les dettes", Format.money(totalRegle)),
                ligne("Total encaissé au SAV", Format.money(totalSav)));
        recap.getStyleClass().add("card-inset");

        Button nouveau = Buttons.outline("Encaisser un règlement", "credit-card");
        nouveau.setVisible(peutEncaisser && c.getBalance().signum() > 0);
        nouveau.setManaged(peutEncaisser && c.getBalance().signum() > 0);
        nouveau.setOnAction(e -> DebtPaymentDialog.ouvrir(c, rafraichir));
        Region espace = new Region();
        HBox.setHgrow(espace, Priority.ALWAYS);
        HBox teteDettes = new HBox(8, titre("Règlements de dettes"), espace, nouveau);
        teteDettes.setAlignment(Pos.CENTER_LEFT);

        return new VBox(14, teteDettes, tableDettes,
                titre("Acomptes & soldes SAV"), tableSav, recap);
    }

    // ------------------------------------------------------------------ helpers

    private static Label titre(String texte) {
        Label l = new Label(texte.toUpperCase());
        l.getStyleClass().add("card-title");
        return l;
    }

    private static HBox ligne(String libelle, String valeur) {
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

    private static ScrollPane defile(Node contenu) {
        ScrollPane sp = new ScrollPane(contenu);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("dialog-scroll");
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }

    private static Badge.BadgeType badgeType(String statut) {
        return switch (statut) {
            case "PRETE" -> Badge.BadgeType.SUCCESS;
            case "ATTENTE_PIECE" -> Badge.BadgeType.WARNING;
            case "ANNULEE" -> Badge.BadgeType.DANGER;
            default -> Badge.BadgeType.INFO;
        };
    }
}
