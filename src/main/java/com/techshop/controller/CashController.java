package com.techshop.controller;

import com.techshop.model.CashMovement;
import com.techshop.model.CashSession;
import com.techshop.service.CashService;
import com.techshop.service.PermissionDefaults;
import com.techshop.service.PermissionService;
import com.techshop.ui.components.Badge;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.EmptyState;
import com.techshop.ui.components.StatCard;
import com.techshop.ui.components.ToastService;
import com.techshop.util.Format;
import com.techshop.util.ValidationException;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Optional;

/**
 * Module Caisse &amp; Finances (SPEC §4.10) : ouverture / clôture d'une session,
 * entrées et sorties d'espèces, synthèse par mode de paiement et historique.
 */
public class CashController {

    @FXML
    private VBox root;
    @FXML
    private HBox headerActions;

    private final CashService caisse = CashService.get();
    private final PermissionService permissions = PermissionService.get();

    private final ObservableList<CashMovement> mouvements = FXCollections.observableArrayList();
    private final ObservableList<CashSession> sessions = FXCollections.observableArrayList();

    private StatCard carteStatut;
    private StatCard carteFonds;
    private StatCard carteEntrees;
    private StatCard carteSorties;
    private StatCard carteAttendu;

    @FXML
    private void initialize() {
        HBox cartes = construireCartes();
        StackPane centre = new StackPane();
        VBox.setVgrow(centre, Priority.ALWAYS);

        root.getChildren().setAll(cartes, centre);
        charger(centre);
    }

    private HBox construireCartes() {
        carteStatut = new StatCard("État de la caisse", "Fermée");
        carteFonds = new StatCard("Fonds initial", "0,00 DA");
        carteEntrees = new StatCard("Encaissements espèces", "0,00 DA");
        carteSorties = new StatCard("Sorties espèces", "0,00 DA");
        carteAttendu = new StatCard("Espèces attendues", "0,00 DA");
        StatCard[] cartes = {carteStatut, carteFonds, carteEntrees, carteSorties, carteAttendu};
        HBox boite = new HBox(12);
        for (StatCard c : cartes) {
            c.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(c, Priority.ALWAYS);
            boite.getChildren().add(c);
        }
        return boite;
    }

    private void charger(StackPane centre) {
        caisse.refreshState();
        construireActions();
        Optional<CashSession> session = caisse.currentSession();

        if (session.isEmpty()) {
            carteStatut.setValue("Fermée");
            carteFonds.setValue("0,00 DA");
            carteEntrees.setValue("0,00 DA");
            carteSorties.setValue("0,00 DA");
            carteAttendu.setValue("0,00 DA");
            mouvements.clear();
        } else {
            CashSession s = session.get();
            CashService.SessionState st = caisse.state(s.getId(), s);
            carteStatut.setValue("Ouverte · " + s.getNumber());
            carteFonds.setValue(Format.money(st.openingFloat()));
            carteEntrees.setValue(Format.money(st.cashIn()));
            carteSorties.setValue(Format.money(st.cashOut()));
            carteAttendu.setValue(Format.money(st.expectedCash()));
            mouvements.setAll(caisse.movements(s.getId()));
        }
        sessions.setAll(caisse.recentSessions(30));

        centre.getChildren().setAll(construireCorps(session.orElse(null)));
    }

    private void construireActions() {
        headerActions.getChildren().clear();
        boolean ouvert = caisse.isOpen();
        if (ouvert) {
            if (peut(PermissionService.Action.CREATE)) {
                Button mvt = Buttons.outline("Mouvement", "repeat");
                mvt.setOnAction(e -> CashMovementDialog.ouvrir(this::recharger));
                headerActions.getChildren().add(mvt);
            }
            if (peut(PermissionService.Action.VALIDATE)) {
                Button cloturer = Buttons.danger("Clôturer la caisse", "lock");
                cloturer.setOnAction(e -> cloturer());
                headerActions.getChildren().add(cloturer);
            }
        } else if (peut(PermissionService.Action.CREATE)) {
            Button ouvrir = Buttons.primary("Ouvrir la caisse", "unlock");
            ouvrir.setOnAction(e -> CashOpenDialog.ouvrir(this::recharger));
            headerActions.getChildren().add(ouvrir);
        }
    }

    private void recharger() {
        if (root.getChildren().size() > 1 && root.getChildren().get(1) instanceof StackPane sp) {
            charger(sp);
        }
    }

    private void cloturer() {
        Optional<CashSession> session = caisse.currentSession();
        if (session.isEmpty()) {
            ToastService.get().warning("Caisse", "Aucune session ouverte.");
            return;
        }
        try {
            CashService.SessionState st = caisse.state(session.get().getId(), session.get());
            CashCloseDialog.ouvrir(st.expectedCash(), this::recharger);
        } catch (ValidationException ex) {
            ToastService.get().error("Caisse", ex.getMessage());
        }
    }

    // -------------------------------------------------------------------- corps

    private Region construireCorps(CashSession session) {
        if (session == null) {
            EmptyState vide = EmptyState.of("briefcase", "Caisse fermée",
                    "Aucune session de caisse n'est ouverte. Ouvrez la caisse pour encaisser "
                            + "les ventes et suivre les mouvements d'espèces.");
            if (peut(PermissionService.Action.CREATE)) {
                vide.withAction("Ouvrir la caisse", () -> CashOpenDialog.ouvrir(this::recharger));
            }
            VBox boite = new VBox(vide, construireHistorique());
            boite.setSpacing(12);
            VBox.setVgrow(vide, Priority.ALWAYS);
            return boite;
        }

        VBox mouvementsCarte = new VBox(8, titreSection("Mouvements de la session"), construireTableMouvements());
        mouvementsCarte.getStyleClass().add("card");
        VBox.setVgrow(mouvementsCarte, Priority.ALWAYS);

        HBox haut = new HBox(12, mouvementsCarte);
        VBox.setVgrow(haut, Priority.ALWAYS);
        HBox.setHgrow(mouvementsCarte, Priority.ALWAYS);

        VBox boite = new VBox(12, haut, construireHistorique());
        VBox.setVgrow(haut, Priority.ALWAYS);
        return boite;
    }

    private Label titreSection(String texte) {
        Label l = new Label(texte);
        l.getStyleClass().add("card-title");
        return l;
    }

    private Region construireTableMouvements() {
        TableView<CashMovement> table = new TableView<>(mouvements);
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);
        Label videMouvements = new Label("Aucun mouvement pour cette session.");
        videMouvements.getStyleClass().add("muted");
        table.setPlaceholder(videMouvements);

        TableColumn<CashMovement, CashMovement> cType = new TableColumn<>("Type");
        cType.setMinWidth(90);
        cType.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cType.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(CashMovement m, boolean empty) {
                super.updateItem(m, empty);
                setText(null);
                setGraphic(empty || m == null ? null
                        : Badge.of(m.typeLabel(), m.isEntree() ? Badge.BadgeType.SUCCESS
                        : Badge.BadgeType.DANGER));
            }
        });

        TableColumn<CashMovement, String> cMotif = new TableColumn<>("Motif");
        cMotif.setMinWidth(220);
        cMotif.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getReason() == null ? "—" : d.getValue().getReason()));
        cMotif.setComparator(Comparator.nullsLast(String::compareToIgnoreCase));

        TableColumn<CashMovement, String> cCategorie = new TableColumn<>("Catégorie");
        cCategorie.setMinWidth(140);
        cCategorie.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getCategoryName() == null ? "—" : d.getValue().getCategoryName()));

        TableColumn<CashMovement, CashMovement> cMontant = new TableColumn<>("Montant");
        cMontant.setMinWidth(130);
        cMontant.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cMontant.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(CashMovement m, boolean empty) {
                super.updateItem(m, empty);
                setText(null);
                if (empty || m == null) {
                    setGraphic(null);
                    return;
                }
                Label montant = new Label((m.isEntree() ? "+ " : "− ") + Format.money(m.getAmount()));
                montant.getStyleClass().addAll("cell-money", m.isEntree() ? "money-pos" : "money-neg");
                setGraphic(montant);
            }
        });

        TableColumn<CashMovement, String> cHeure = new TableColumn<>("Heure");
        cHeure.setMinWidth(120);
        cHeure.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getMovementDate() == null ? "—"
                        : Format.dateTime(d.getValue().getMovementDate())));
        cHeure.setComparator(Comparator.nullsLast(String::compareTo));

        TableColumn<CashMovement, String> cUser = new TableColumn<>("Utilisateur");
        cUser.setMinWidth(130);
        cUser.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getUsername() == null ? "—" : d.getValue().getUsername()));

        table.getColumns().setAll(cType, cMotif, cCategorie, cMontant, cHeure, cUser);
        return table;
    }

    private Region construireHistorique() {
        TableView<CashSession> table = new TableView<>(sessions);
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(220);
        Label videSessions = new Label("Aucune session de caisse.");
        videSessions.getStyleClass().add("muted");
        table.setPlaceholder(videSessions);

        TableColumn<CashSession, String> cNum = new TableColumn<>("Session");
        cNum.setMinWidth(120);
        cNum.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getNumber()));

        TableColumn<CashSession, String> cUser = new TableColumn<>("Ouverte par");
        cUser.setMinWidth(150);
        cUser.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getUsername() == null ? "—" : d.getValue().getUsername()));

        TableColumn<CashSession, String> cOuverture = new TableColumn<>("Ouverture");
        cOuverture.setMinWidth(150);
        cOuverture.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.dateTime(
                d.getValue().getOpenedAt())));

        TableColumn<CashSession, String> cCloture = new TableColumn<>("Clôture");
        cCloture.setMinWidth(150);
        cCloture.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getClosedAt() == null ? "—" : Format.dateTime(d.getValue().getClosedAt())));

        TableColumn<CashSession, String> cFonds = new TableColumn<>("Fonds");
        cFonds.setMinWidth(110);
        cFonds.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                Format.money(d.getValue().getOpeningFloat())));

        TableColumn<CashSession, String> cVentes = new TableColumn<>("Ventes");
        cVentes.setMinWidth(120);
        cVentes.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                Format.money(d.getValue().getTotalSales())));

        TableColumn<CashSession, String> cAttendu = new TableColumn<>("Attendu");
        cAttendu.setMinWidth(110);
        cAttendu.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                Format.money(d.getValue().getExpectedCash())));

        TableColumn<CashSession, String> cCompte = new TableColumn<>("Compté");
        cCompte.setMinWidth(110);
        cCompte.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getCountedCash() == null ? "—"
                        : Format.money(d.getValue().getCountedCash())));

        TableColumn<CashSession, CashSession> cEcart = new TableColumn<>("Écart");
        cEcart.setMinWidth(120);
        cEcart.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cEcart.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(CashSession s, boolean empty) {
                super.updateItem(s, empty);
                setText(null);
                if (empty || s == null || s.getDifference() == null) {
                    setGraphic(null);
                    return;
                }
                BigDecimal d = s.getDifference();
                Label l = new Label((d.signum() > 0 ? "+ " : d.signum() < 0 ? "− " : "")
                        + Format.money(d));
                l.getStyleClass().addAll("cell-money", d.signum() == 0 ? ""
                        : d.signum() > 0 ? "money-pos" : "money-neg");
                setGraphic(l);
            }
        });

        TableColumn<CashSession, CashSession> cStatut = new TableColumn<>("Statut");
        cStatut.setMinWidth(110);
        cStatut.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cStatut.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(CashSession s, boolean empty) {
                super.updateItem(s, empty);
                setText(null);
                setGraphic(empty || s == null ? null : Badge.of(s.statusLabel(),
                        s.isOpen() ? Badge.BadgeType.SUCCESS : Badge.BadgeType.NEUTRAL));
            }
        });

        table.getColumns().setAll(cNum, cUser, cOuverture, cCloture, cFonds, cVentes, cAttendu,
                cCompte, cEcart, cStatut);

        VBox carte = new VBox(8, titreSection("Historique des sessions"), table);
        carte.getStyleClass().add("card");
        return carte;
    }

    private boolean peut(PermissionService.Action action) {
        return permissions.can(PermissionDefaults.M_CASH, action);
    }
}
