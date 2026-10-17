package com.techshop.controller;

import com.techshop.dao.RepairTicketDao;
import com.techshop.dao.UserDao;
import com.techshop.model.RepairStatus;
import com.techshop.model.RepairTicket;
import com.techshop.model.Role;
import com.techshop.model.User;
import com.techshop.service.MenuRegistry;
import com.techshop.service.NavigationService;
import com.techshop.service.PermissionDefaults;
import com.techshop.service.PermissionService;
import com.techshop.service.RepairService;
import com.techshop.ui.components.Avatar;
import com.techshop.ui.components.Badge;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.EmptyState;
import com.techshop.ui.components.ToastService;
import com.techshop.util.Format;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Écran Atelier / SAV (SPEC §4.9) : vue Kanban par statut (glisser-déposer pour
 * changer d'étape) et vue tableau filtrable. Aucune logique métier : tout passe
 * par {@link RepairService}.
 */
public class RepairsController {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> techFilter;
    @FXML private javafx.scene.control.CheckBox lateFilter;
    @FXML private ToggleButton kanbanToggle;
    @FXML private ToggleButton tableToggle;
    @FXML private HBox headerActions;
    @FXML private StackPane root;

    private final RepairService repairs = RepairService.get();
    private final UserDao users = new UserDao();
    private final ObservableList<RepairTicket> lignes = FXCollections.observableArrayList();
    private final List<User> techniciens = new ArrayList<>();

    private boolean kanban = true;

    @FXML
    private void initialize() {
        headerActions.getChildren().clear();
        if (PermissionService.get().can(PermissionDefaults.M_REPAIRS, PermissionService.Action.CREATE)) {
            Button nouveau = Buttons.primary("Nouveau dossier", "plus");
            nouveau.setOnAction(e -> RepairDepositDialog.ouvrir(this::charger));
            headerActions.getChildren().add(nouveau);
        }
        Button raf = Buttons.outline("Rafraîchir", "refresh-cw");
        raf.setOnAction(e -> charger());
        headerActions.getChildren().add(raf);

        statusFilter.getItems().add("Tous les statuts");
        for (String s : RepairStatus.BOARD) {
            statusFilter.getItems().add(RepairStatus.label(s));
        }
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.valueProperty().addListener((o, a, b) -> charger());

        try {
            techniciens.addAll(users.search(null, Role.TECHNICIEN, true));
        } catch (Exception ignored) {
        }
        techFilter.getItems().add("Tous les techniciens");
        for (User u : techniciens) {
            techFilter.getItems().add(u.getFullName());
        }
        techFilter.getSelectionModel().selectFirst();
        techFilter.valueProperty().addListener((o, a, b) -> charger());

        searchField.textProperty().addListener((o, a, b) -> charger());
        lateFilter.selectedProperty().addListener((o, a, b) -> charger());

        ToggleGroup groupe = new ToggleGroup();
        kanbanToggle.setToggleGroup(groupe);
        tableToggle.setToggleGroup(groupe);
        kanbanToggle.setSelected(true);
        kanbanToggle.selectedProperty().addListener((o, a, b) -> {
            kanban = b;
            charger();
        });
        tableToggle.selectedProperty().addListener((o, a, b) -> {
            if (b) {
                kanban = false;
                charger();
            }
        });

        charger();
    }

    private String statutChoisi() {
        int i = statusFilter.getSelectionModel().getSelectedIndex();
        return i <= 0 ? null : RepairStatus.BOARD.get(i - 1);
    }

    private Integer technicienChoisi() {
        int i = techFilter.getSelectionModel().getSelectedIndex();
        return i <= 0 ? null : techniciens.get(i - 1).getId();
    }

    private void charger() {
        RepairTicketDao.Filter f = new RepairTicketDao.Filter(
                searchField.getText() == null || searchField.getText().isBlank()
                        ? null : searchField.getText().trim(),
                statutChoisi(), technicienChoisi(), null, null,
                lateFilter.isSelected() ? Boolean.TRUE : null);
        try {
            lignes.setAll(repairs.search(f));
        } catch (RuntimeException e) {
            lignes.clear();
        }
        root.getChildren().setAll(kanban ? construireKanban() : construireTableau());
    }

    // --------------------------------------------------------------- Kanban

    private ScrollPane construireKanban() {
        Map<String, Long> counts = repairs.statusCounts();
        HBox colonnes = new HBox(14);

        for (String statut : RepairStatus.BOARD) {
            colonnes.getChildren().add(construireColonne(statut, counts.getOrDefault(statut, 0L)));
        }

        ScrollPane scroll = new ScrollPane(colonnes);
        scroll.getStyleClass().add("kanban-scroll");
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return scroll;
    }

    private VBox construireColonne(String statut, long total) {
        Label titre = new Label(RepairStatus.label(statut));
        titre.getStyleClass().add("kanban-col-title");
        Label compte = new Label(String.valueOf(total));
        compte.getStyleClass().addAll("badge", badgeClassFor(statut));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox entete = new HBox(8, new Dot(statut), titre, spacer, compte);
        entete.setAlignment(Pos.CENTER_LEFT);
        entete.getStyleClass().add("kanban-col-header");

        VBox cartes = new VBox(10);
        cartes.getStyleClass().add("kanban-col-body");
        long affiches = 0;
        for (RepairTicket t : lignes) {
            if (statut.equals(t.getStatus())) {
                cartes.getChildren().add(carte(t));
                affiches++;
            }
        }
        if (affiches == 0) {
            Label vide = new Label("Aucun dossier");
            vide.getStyleClass().add("kanban-empty");
            cartes.getChildren().add(vide);
        }

        ScrollPane scrollCartes = new ScrollPane(cartes);
        scrollCartes.getStyleClass().add("kanban-col-scroll");
        scrollCartes.setFitToWidth(true);
        scrollCartes.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollCartes, Priority.ALWAYS);

        VBox colonne = new VBox(entete, scrollCartes);
        colonne.getStyleClass().add("kanban-col");
        colonne.setPrefWidth(276);
        colonne.setMinWidth(276);
        colonne.setMaxWidth(276);

        colonne.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        colonne.setOnDragEntered(e -> colonne.getStyleClass().add("drop-target"));
        colonne.setOnDragExited(e -> colonne.getStyleClass().remove("drop-target"));
        colonne.setOnDragDropped(e -> {
            colonne.getStyleClass().remove("drop-target");
            boolean ok = false;
            if (e.getDragboard().hasString()) {
                try {
                    deplace(Integer.parseInt(e.getDragboard().getString()), statut);
                    ok = true;
                } catch (NumberFormatException ignored) {
                }
            }
            e.setDropCompleted(ok);
            e.consume();
        });
        return colonne;
    }

    private Node carte(RepairTicket t) {
        Label numero = new Label(t.getNumber());
        numero.getStyleClass().add("kanban-card-number");
        Region sp1 = new Region();
        HBox.setHgrow(sp1, Priority.ALWAYS);
        HBox haut = new HBox(6, numero, sp1, badge(t));
        haut.setAlignment(Pos.CENTER_LEFT);

        Label client = new Label(t.customerLabel());
        client.getStyleClass().add("kanban-card-client");

        Label appareil = new Label(t.deviceLabel() + (t.getSerialOrImei() == null || t.getSerialOrImei().isBlank()
                ? "" : "  ·  " + t.getSerialOrImei()));
        appareil.getStyleClass().add("kanban-card-device");

        String panne = t.getReportedIssue() == null ? "" : t.getReportedIssue();
        Label panneLabel = new Label(panne.length() > 78 ? panne.substring(0, 78) + "…" : panne);
        panneLabel.getStyleClass().add("kanban-card-fault");
        panneLabel.setWrapText(true);

        Label dateLabel = new Label(t.getPromisedDate() == null
                ? "Date non planifiée" : "Prévu le " + Format.date(t.getPromisedDate()));
        dateLabel.getStyleClass().add("kanban-card-date");
        if (t.isLate()) {
            dateLabel.setText("En retard de " + t.lateDays() + " j");
            dateLabel.getStyleClass().add("kanban-card-late");
        }

        Label montant = new Label(Format.money(t.getTotal()));
        montant.getStyleClass().add("kanban-card-amount");

        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);
        Node avatar = Avatar.of(t.getTechnicianName() == null ? "Non assigné" : t.getTechnicianName(), 24);
        HBox bas = new HBox(8, avatar, montant, sp2, dateLabel);
        bas.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(6, haut, client, appareil, panneLabel, bas);
        card.getStyleClass().add("kanban-card");
        card.setOnMouseClicked(e -> ouvrirFiche(t));
        card.setOnDragDetected(e -> {
            var db = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent cc = new ClipboardContent();
            cc.putString(String.valueOf(t.getId()));
            db.setContent(cc);
            card.setOpacity(0.5);
            e.consume();
        });
        card.setOnDragDone(e -> card.setOpacity(1));
        return card;
    }

    private void deplace(int repairId, String statut) {
        RepairTicket t;
        try {
            t = repairs.byId(repairId);
        } catch (RuntimeException e) {
            return;
        }
        if (statut.equals(t.getStatus())) {
            return;
        }
        if (RepairStatus.LIVREE.equals(statut)) {
            if (RepairStatus.PRETE.equals(t.getStatus())) {
                RepairDeliverDialog.ouvrir(t, this::charger);
            } else {
                ToastService.get().warning("Remise impossible",
                        "L'appareil doit d'abord être au statut « Prête ».");
            }
            return;
        }
        if (!RepairStatus.canTransition(t.getStatus(), statut)) {
            ToastService.get().warning("Transition impossible",
                    RepairStatus.label(t.getStatus()) + " → " + RepairStatus.label(statut)
                            + " n'est pas autorisée.");
            return;
        }
        try {
            repairs.changeStatus(repairId, statut, "Déplacement Kanban");
            ToastService.get().success("Dossier " + t.getNumber(),
                    "Statut : " + RepairStatus.label(statut));
            charger();
        } catch (RuntimeException e) {
            ToastService.get().error("Changement de statut", e.getMessage());
        }
    }

    // --------------------------------------------------------------- Tableau

    private TableView<RepairTicket> construireTableau() {
        TableView<RepairTicket> table = new TableView<>(lignes);
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(EmptyState.of("tool", "Aucun dossier",
                "Aucune réparation ne correspond aux filtres sélectionnés."));
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<RepairTicket, String> cNum = new TableColumn<>("N°");
        cNum.setMinWidth(90);
        cNum.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getNumber()));

        TableColumn<RepairTicket, String> cClient = new TableColumn<>("Client");
        cClient.setMinWidth(150);
        cClient.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().customerLabel()));

        TableColumn<RepairTicket, String> cAppareil = new TableColumn<>("Appareil");
        cAppareil.setMinWidth(170);
        cAppareil.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().deviceLabel()));

        TableColumn<RepairTicket, String> cPanne = new TableColumn<>("Panne déclarée");
        cPanne.setMinWidth(200);
        cPanne.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getReportedIssue() == null ? "—" : d.getValue().getReportedIssue()));

        TableColumn<RepairTicket, String> cTech = new TableColumn<>("Technicien");
        cTech.setMinWidth(130);
        cTech.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getTechnicianName() == null ? "—" : d.getValue().getTechnicianName()));

        TableColumn<RepairTicket, String> cRecu = new TableColumn<>("Reçu le");
        cRecu.setMinWidth(130);
        cRecu.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                Format.dateTime(d.getValue().getReceivedAt())));

        TableColumn<RepairTicket, RepairTicket> cStat = new TableColumn<>("Statut");
        cStat.setMinWidth(140);
        cStat.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cStat.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(RepairTicket t, boolean empty) {
                super.updateItem(t, empty);
                setText(null);
                setGraphic(empty || t == null ? null : badge(t));
            }
        });

        TableColumn<RepairTicket, String> cTotal = new TableColumn<>("Total");
        cTotal.setMinWidth(110);
        cTotal.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().getTotal())));

        TableColumn<RepairTicket, String> cDue = new TableColumn<>("Reste dû");
        cDue.setMinWidth(110);
        cDue.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().getDueAmount())));

        TableColumn<RepairTicket, RepairTicket> cAct = new TableColumn<>("");
        cAct.setMinWidth(80);
        cAct.setMaxWidth(100);
        cAct.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cAct.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(RepairTicket t, boolean empty) {
                super.updateItem(t, empty);
                setText(null);
                if (empty || t == null) {
                    setGraphic(null);
                    return;
                }
                Button voir = Buttons.iconCompact("eye", "Ouvrir la fiche");
                voir.setOnAction(e -> ouvrirFiche(t));
                setGraphic(voir);
            }
        });

        table.getColumns().setAll(cNum, cClient, cAppareil, cPanne, cTech, cRecu, cStat, cTotal,
                cDue, cAct);
        return table;
    }

    private void ouvrirFiche(RepairTicket t) {
        RepairSheetDialog.ouvrir(t, this::charger);
    }

    // --------------------------------------------------------------- util

    private Badge badge(RepairTicket t) {
        return Badge.of(t.statusLabel(), badgeTypeFor(t.getStatus()));
    }

    private static Badge.BadgeType badgeTypeFor(String statut) {
        return switch (statut) {
            case RepairStatus.PRETE -> Badge.BadgeType.SUCCESS;
            case RepairStatus.ATTENTE_PIECE -> Badge.BadgeType.WARNING;
            case RepairStatus.ANNULEE -> Badge.BadgeType.DANGER;
            case RepairStatus.RECU, RepairStatus.DIAGNOSTIC, RepairStatus.EN_REPARATION ->
                    Badge.BadgeType.INFO;
            default -> Badge.BadgeType.NEUTRAL;
        };
    }

    private static String badgeClassFor(String statut) {
        return switch (statut) {
            case RepairStatus.PRETE -> "badge-success";
            case RepairStatus.ATTENTE_PIECE -> "badge-warning";
            case RepairStatus.ANNULEE -> "badge-danger";
            case RepairStatus.EN_REPARATION -> "badge-info";
            default -> "badge-neutral";
        };
    }

    /** Ouvre directement la fiche d'un dossier (appel depuis le tableau de bord). */
    public static void openTicket(int repairId) {
        var item = MenuRegistry.byId("repairs");
        if (item != null) {
            NavigationService.get().navigate(item);
        }
        Platform.runLater(() -> {
            try {
                RepairSheetDialog.ouvrir(RepairService.get().byId(repairId), null);
            } catch (RuntimeException ignored) {
            }
        });
    }

    /** Pastille de couleur de la colonne Kanban. */
    private static final class Dot extends Region {
        Dot(String statut) {
            getStyleClass().add("kanban-dot");
            setStyle("-fx-background-color: " + couleur(statut) + ";");
        }

        private static String couleur(String statut) {
            return switch (statut) {
                case RepairStatus.RECU, RepairStatus.DIAGNOSTIC -> "#0EA5E9";
                case RepairStatus.ATTENTE_PIECE -> "#D97706";
                case RepairStatus.EN_REPARATION -> "#4F46E5";
                case RepairStatus.PRETE -> "#16A34A";
                case RepairStatus.LIVREE -> "#64748B";
                case RepairStatus.ANNULEE -> "#DC2626";
                default -> "#64748B";
            };
        }
    }
}
