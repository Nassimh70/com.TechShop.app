package com.techshop.controller;

import com.techshop.app.AppShell;
import com.techshop.config.ThemeManager;
import com.techshop.model.NavItem;
import com.techshop.model.Product;
import com.techshop.model.RepairTicket;
import com.techshop.model.Role;
import com.techshop.model.StockStatus;
import com.techshop.service.AuthService;
import com.techshop.service.CashService;
import com.techshop.service.MenuRegistry;
import com.techshop.service.PermissionService;
import com.techshop.service.SessionContext;
import com.techshop.service.PermissionDefaults;
import com.techshop.service.RepairService;
import com.techshop.service.StockService;
import com.techshop.service.NavigationService;
import com.techshop.ui.Branding;
import com.techshop.ui.Icons;
import com.techshop.ui.components.Avatar;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.Keycap;
import com.techshop.ui.components.ToastService;
import com.techshop.util.Format;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur de la fenêtre principale : barre latérale (repliable, filtrée par
 * rôle), barre supérieure, zone de contenu, palette de commandes Ctrl+K,
 * bascule de thème, notifications. Aucune logique métier ici (SPEC §1) :
 * tout passe par les services.
 */
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    private static final double SIDEBAR_EXPANDED = 248;
    private static final double SIDEBAR_COLLAPSED = 72;

    // --- Structure ---
    @FXML
    private StackPane appRoot;
    @FXML
    private VBox sidebar;
    @FXML
    private StackPane logoBadge;
    @FXML
    private Button collapseBtn;
    @FXML
    private ScrollPane navScroll;
    @FXML
    private VBox navBox;
    @FXML
    private StackPane avatarHolder;
    @FXML
    private Label userName;
    @FXML
    private Label userRole;
    @FXML
    private Button logoutBtn;

    // --- Barre supérieure ---
    @FXML
    private Label pageTitle;
    @FXML
    private Label breadcrumb;
    @FXML
    private Button searchBtn;
    @FXML
    private Button themeBtn;
    @FXML
    private Button bellBtn;
    @FXML
    private Label cashChip;
    @FXML
    private MenuButton userMenu;
    @FXML
    private RadioMenuItem roleAdminItem;
    @FXML
    private RadioMenuItem roleVendorItem;
    @FXML
    private RadioMenuItem roleTechItem;
    @FXML
    private MenuItem logoutItem;

    // --- Contenu + overlay ---
    @FXML
    private StackPane contentHost;
    @FXML
    private StackPane overlayHost;

    // --- État interne ---
    private final Map<String, Node> navNodes = new HashMap<>();
    private final Map<String, Label> navLabels = new HashMap<>();
    private boolean collapsed = false;
    private int notificationCount = 0;
    private Label bellBadge;
    private Region cashDot;
    private Label cashText;

    // ---------------------------------------------------------------- initialisation

    @FXML
    private void initialize() {
        log.info("Initialisation de la fenêtre principale");

        // Overlay : toasts en haut à droite, dialogues centrés
        ToastService.get().init(overlayHost);
        DialogService.get().init(overlayHost);

        // Navigation centrale
        NavigationService nav = NavigationService.get();
        nav.init(contentHost);
        nav.setOnScreenChanged(this::onScreenChanged);

        buildSidebar();
        buildHeader();
        wireRolePreview();
        installShortcuts();
    }

    /** Après affichage : navigation initiale. */
    public void start() {
        NavigationService.get().navigate("dashboard");
    }

    // ------------------------------------------------------------------- sidebar

    private void buildSidebar() {
        navBox.getChildren().clear();
        navNodes.clear();
        navLabels.clear();

        Branding.mount(logoBadge, 36);
        collapseBtn.setGraphic(Icons.feather(collapsed ? "chevron-right" : "chevron-left", 16));
        collapseBtn.setTooltip(new Tooltip(collapsed ? "Déplier le menu" : "Replier le menu"));

        // Utilisateur connecté en bas de la barre latérale
        var user = SessionContext.get().getUser();
        String name = user == null ? "—" : user.getFullName();
        avatarHolder.getChildren().setAll(Avatar.of(name, 32));
        userName.setText(name);
        updateRoleLabel();
        logoutBtn.setGraphic(Icons.feather("log-out", 16));
        logoutBtn.setTooltip(new Tooltip("Se déconnecter"));

        Role role = SessionContext.get().getRole();
        String currentSection = null;
        for (NavItem item : PermissionService.get().visibleItems(role)) {
            if (!item.getSection().equals(currentSection)) {
                currentSection = item.getSection();
                Label section = new Label(sectionLabel(currentSection).toUpperCase());
                section.getStyleClass().add("nav-section");
                navBox.getChildren().add(section);
            }
            navBox.getChildren().add(sidebarItem(item));
        }

        // Entrée active restaurée après reconstruction
        NavItem cur = NavigationService.get().getCurrent();
        if (cur != null) {
            highlight(cur.getId());
        }
    }

    private Node sidebarItem(NavItem item) {
        Region indicator = new Region();
        indicator.getStyleClass().add("indicator");

        Label label = new Label(item.getTitle());
        label.getStyleClass().add("sidebar-item-label");
        navLabels.put(item.getId(), label);

        HBox row = new HBox(indicator, Icons.feather(item.getIcon(), 18), label);
        row.getStyleClass().add("sidebar-item");
        row.setAlignment(Pos.CENTER_LEFT);
        Tooltip.install(row, new Tooltip(item.getTitle()));
        row.setOnMouseClicked(e -> NavigationService.get().navigate(item));

        navNodes.put(item.getId(), row);
        return row;
    }

    private void highlight(String id) {
        navNodes.forEach((key, node) -> node.getStyleClass().remove("active"));
        Node node = navNodes.get(id);
        if (node != null) {
            node.getStyleClass().add("active");
            scrollIntoView(node);
        }
    }

    /** Fait défiler le menu pour que l'entrée active soit toujours visible. */
    private void scrollIntoView(Node node) {
        if (navScroll == null) {
            return;
        }
        Platform.runLater(() -> {
            double viewport = navScroll.getViewportBounds().getHeight();
            double max = navBox.getHeight() - viewport;
            if (max <= 0) {
                return;
            }
            double top = node.getBoundsInParent().getMinY();
            double bottom = node.getBoundsInParent().getMaxY();
            double current = navScroll.getVvalue() * max;
            double target = current;
            if (top < current) {
                target = top;
            } else if (bottom > current + viewport) {
                target = bottom - viewport;
            }
            if (target != current) {
                navScroll.setVvalue(Math.max(0, Math.min(1, target / max)));
            }
        });
    }

    private void onScreenChanged(NavItem item) {
        CashService.get().refreshState();
        if (item == null) {
            pageTitle.setText("TechShop Manager");
            breadcrumb.setText("Accueil");
            highlight("");
            return;
        }
        pageTitle.setText(item.getTitle());
        breadcrumb.setText("Accueil  ›  " + sectionLabel(item.getSection()));
        highlight(item.getId());
    }

    private static String sectionLabel(String section) {
        return switch (section) {
            case MenuRegistry.SEC_OVERVIEW -> "Pilotage";
            case MenuRegistry.SEC_SALES -> "Ventes & clients";
            case MenuRegistry.SEC_CATALOG -> "Catalogue & stock";
            case MenuRegistry.SEC_SERVICE -> "Atelier";
            case MenuRegistry.SEC_FINANCE -> "Finances";
            case MenuRegistry.SEC_ADMIN -> "Administration";
            default -> "Navigation";
        };
    }

    @FXML
    private void onToggleSidebar(javafx.event.ActionEvent event) {
        collapsed = !collapsed;
        sidebar.getStyleClass().remove("collapsed");
        if (collapsed) {
            sidebar.getStyleClass().add("collapsed");
        }
        // Repli fluide mais simple (160 ms)
        Timeline t = new Timeline(new KeyFrame(Duration.millis(160),
                new KeyValue(sidebar.prefWidthProperty(),
                        collapsed ? SIDEBAR_COLLAPSED : SIDEBAR_EXPANDED)));
        t.play();
        collapseBtn.setGraphic(Icons.feather(collapsed ? "chevron-right" : "chevron-left", 16));
        collapseBtn.setTooltip(new Tooltip(collapsed ? "Déplier le menu" : "Replier le menu"));
    }

    // -------------------------------------------------------------- barre supérieure

    private void buildHeader() {
        // Bouton « Rechercher… Ctrl+K »
        Label hint = new Label("Rechercher…");
        hint.getStyleClass().add("hint");
        searchBtn.getStyleClass().add("search-trigger");
        searchBtn.setGraphic(Icons.feather("search", 15));
        searchBtn.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        searchBtn.setGraphicTextGap(8);
        searchBtn.setText("Rechercher…");
        searchBtn.setGraphic(new HBox(8, Icons.feather("search", 15), hint, Keycap.of("Ctrl+K")));
        searchBtn.setText(null);

        refreshThemeIcon();

        // Cloche de notifications (alertes de stock réelles)
        bellBadge = new Label();
        bellBadge.getStyleClass().add("bell-badge");
        bellBadge.setId("bellBadge");
        StackPane bellGraphic = new StackPane(Icons.feather("bell", 17), bellBadge);
        StackPane.setAlignment(bellBadge, Pos.TOP_RIGHT);
        bellBtn.setGraphic(bellGraphic);
        bellBtn.setTooltip(new Tooltip("Alertes de stock"));
        rafraichirNotifications();

        // Indicateur de caisse (état réel du module Caisse)
        cashDot = dot();
        cashText = new Label("Caisse fermée");
        HBox cashContent = new HBox(6, cashDot, cashText);
        cashContent.setAlignment(Pos.CENTER_LEFT);
        cashChip.setGraphic(cashContent);
        cashChip.setText(null);
        cashChip.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        cashContent.setMouseTransparent(true);
        rafraichirCaisse();
        CashService.get().openProperty().addListener((o, a, b) -> rafraichirCaisse());
        CashService.get().sessionNumberProperty().addListener((o, a, b) -> rafraichirCaisse());

        // Menu utilisateur : avatar + nom + rôle
        var user = SessionContext.get().getUser();
        String name = user == null ? "—" : user.getFullName();
        Label fullName = new Label(name);
        fullName.getStyleClass().add("user-fullname");
        Label roleLabel = new Label(SessionContext.get().getRole().getLabel());
        roleLabel.getStyleClass().add("user-role-label");
        VBox texts = new VBox(0, fullName, roleLabel);
        HBox graphic = new HBox(8, Avatar.of(name, 28), texts);
        graphic.setAlignment(Pos.CENTER_LEFT);
        userMenu.setGraphic(graphic);
        userMenu.setText(" ");
    }

    private Region dot() {
        Region dot = new Region();
        dot.getStyleClass().add("cash-dot");
        return dot;
    }

    private void refreshThemeIcon() {
        themeBtn.setGraphic(Icons.feather(ThemeManager.isDark() ? "sun" : "moon", 17));
        themeBtn.setTooltip(new Tooltip(ThemeManager.isDark()
                ? "Passer au thème clair" : "Passer au thème sombre"));
    }

    @FXML
    private void onToggleTheme(javafx.event.ActionEvent event) {
        toggleTheme();
    }

    /** Bascule le thème (appelé aussi par la palette de commandes). */
    private void toggleTheme() {
        ThemeManager.toggle();
        refreshThemeIcon();
        ToastService.get().info("Apparence",
                ThemeManager.isDark() ? "Thème sombre activé" : "Thème clair activé");
    }

    @FXML
    private void onCashChip(javafx.scene.input.MouseEvent event) {
        CashService.get().refreshState();
        if (CashService.get().isOpen()) {
            ouvrirModule("cash");
        } else {
            if (PermissionService.get().can(PermissionDefaults.M_CASH,
                    PermissionService.Action.CREATE)) {
                CashOpenDialog.ouvrir(() -> {
                    CashService.get().refreshState();
                    ouvrirModule("cash");
                });
            } else {
                ToastService.get().warning("Caisse fermée",
                        "Aucune session de caisse ouverte.");
            }
        }
    }

    /** Met à jour la pastille de caisse selon l'état réel (CashService). */
    private void rafraichirCaisse() {
        if (cashChip == null || cashText == null) {
            return;
        }
        boolean ouvert = CashService.get().openProperty().get();
        cashChip.getStyleClass().removeAll("open", "closed");
        cashChip.getStyleClass().add(ouvert ? "open" : "closed");
        cashText.setText(ouvert
                ? "Caisse ouverte"
                : "Caisse fermée");
        cashChip.setTooltip(new Tooltip(ouvert
                ? "Session " + CashService.get().sessionNumberProperty().get() + " ouverte — gérer la caisse"
                : "Cliquez pour ouvrir une session de caisse"));
    }

    @FXML
    private void onShowNotifications(javafx.event.ActionEvent event) {
        ContextMenu menu = new ContextMenu();
        List<Product> alertes = StockService.get().alerts();
        if (alertes.isEmpty()) {
            menu.getItems().add(notificationItem("check-circle", "Aucune alerte de stock"));
        } else {
            int max = Math.min(6, alertes.size());
            for (int i = 0; i < max; i++) {
                Product p = alertes.get(i);
                boolean rupture = p.stockStatus() == StockStatus.OUT;
                String texte = p.getName() + " — " + (rupture
                        ? "rupture de stock"
                        : "stock faible (" + p.getQuantity() + " restant"
                        + (p.getQuantity() > 1 ? "s" : "") + ")");
                MenuItem item = notificationItem(rupture ? "alert-octagon" : "alert-triangle", texte);
                item.setOnAction(e -> ouvrirModule("stock"));
                menu.getItems().add(item);
            }
            if (alertes.size() > max) {
                menu.getItems().add(notificationItem("more-horizontal",
                        "… et " + (alertes.size() - max) + " autre(s)"));
            }
        }

        // Alertes SAV (SPEC §4.9) : appareils prêts à livrer et dossiers en retard.
        List<RepairTicket> prets = new ArrayList<>();
        List<RepairTicket> retards = new ArrayList<>();
        try {
            prets.addAll(RepairService.get().readyForPickup());
            retards.addAll(RepairService.get().late());
        } catch (RuntimeException e) {
            log.warn("Alertes SAV indisponibles", e);
        }
        if (!prets.isEmpty() || !retards.isEmpty()) {
            menu.getItems().add(new SeparatorMenuItem());
        }
        int maxSav = 4;
        for (int i = 0; i < Math.min(maxSav, prets.size()); i++) {
            RepairTicket t = prets.get(i);
            MenuItem item = notificationItem("check-circle",
                    t.getNumber() + " — " + t.deviceLabel() + " prêt à livrer");
            item.setOnAction(e -> RepairsController.openTicket(t.getId()));
            menu.getItems().add(item);
        }
        int restants = prets.size() - maxSav;
        for (int i = 0; i < Math.min(maxSav, retards.size()); i++) {
            RepairTicket t = retards.get(i);
            MenuItem item = notificationItem("clock",
                    t.getNumber() + " — en retard de " + t.lateDays() + " j (" + t.customerLabel() + ")");
            item.setOnAction(e -> RepairsController.openTicket(t.getId()));
            menu.getItems().add(item);
        }
        if (restants > 0) {
            menu.getItems().add(notificationItem("more-horizontal",
                    "… et " + restants + " autre(s) dossier(s) prêt(s)"));
        }
        if (retards.size() > maxSav) {
            menu.getItems().add(notificationItem("more-horizontal",
                    "… et " + (retards.size() - maxSav) + " autre(s) en retard"));
        }

        menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(notificationItem("check", "Tout marquer comme lu"));
        menu.show(bellBtn, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    /** Recalcule le compteur de la cloche : alertes de stock + SAV à suivre. */
    private void rafraichirNotifications() {
        int alertes = 0;
        try {
            StockService.Summary s = StockService.get().summary();
            alertes = (int) (s.low() + s.out());
        } catch (RuntimeException e) {
            log.warn("Alertes de stock indisponibles", e);
        }
        try {
            alertes += RepairService.get().readyForPickup().size();
            alertes += RepairService.get().late().size();
        } catch (RuntimeException e) {
            log.warn("Alertes SAV indisponibles", e);
        }
        notificationCount = alertes;
        if (bellBadge != null) {
            bellBadge.setText(String.valueOf(alertes));
            boolean visible = alertes > 0;
            bellBadge.setVisible(visible);
            bellBadge.setManaged(visible);
        }
    }

    /** Navigue vers un module par son identifiant. */
    private void ouvrirModule(String moduleId) {
        NavItem item = MenuRegistry.byId(moduleId);
        if (item != null) {
            NavigationService.get().navigate(item);
        }
    }

    private MenuItem notificationItem(String icon, String text) {
        MenuItem item = new MenuItem(text);
        item.setGraphic(Icons.feather(icon, 15));
        item.setOnAction(e -> {
            if (text.startsWith("Tout marquer")) {
                notificationCount = 0;
                Node n = bellBtn.getGraphic().lookup("#bellBadge");
                if (n instanceof Label l) {
                    l.setText("0");
                    l.setVisible(false);
                    l.setManaged(false);
                }
                ToastService.get().success("Notifications", "Toutes les alertes sont marquées comme lues");
            } else {
                ToastService.get().info("Notifications", text + " — détail disponible avec le module concerné");
            }
        });
        return item;
    }

    @FXML
    private void onLogout(javafx.event.ActionEvent event) {
        DialogService.get()
                .confirm("Se déconnecter", "Voulez-vous vraiment fermer la session en cours ?",
                        "Se déconnecter", true)
                .thenAccept(ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        AuthService.get().logout();
                        AppShell.showLogin();
                    }
                });
    }

    // -------------------------------------------------------------- aperçu du rôle

    private void wireRolePreview() {
        roleAdminItem.setOnAction(e -> applyRole(Role.ADMIN));
        roleVendorItem.setOnAction(e -> applyRole(Role.VENDEUR));
        roleTechItem.setOnAction(e -> applyRole(Role.TECHNICIEN));

        // Déconnexion depuis le menu utilisateur
        logoutItem.setGraphic(Icons.feather("log-out", 15));

        Role r = SessionContext.get().getRole();
        roleAdminItem.setSelected(r == Role.ADMIN);
        roleVendorItem.setSelected(r == Role.VENDEUR);
        roleTechItem.setSelected(r == Role.TECHNICIEN);
    }

    private void applyRole(Role role) {
        SessionContext.get().setDisplayRole(role);
        buildSidebar();
        buildHeader();
        updateRoleLabel();
        NavigationService.get().navigate("dashboard");
        ToastService.get().info("Aperçu du rôle",
                "Menu filtré pour le rôle « " + role.getLabel() + " » (démonstration)");
    }

    private void updateRoleLabel() {
        Role real = SessionContext.get().realRole();
        Role display = SessionContext.get().getRole();
        userRole.setText(display == real ? real.getLabel() : display.getLabel() + " (aperçu)");
    }

    // ------------------------------------------------------------- palette Ctrl+K

    private void installShortcuts() {
        appRoot.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null) {
                return;
            }
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN),
                    this::onOpenPalette);
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.ESCAPE, KeyCombination.CONTROL_DOWN),
                    () -> {
                    });
        });
    }

    @FXML
    private void onOpenPalette() {
        TextField input = new TextField();
        input.getStyleClass().add("palette-input");
        input.setPromptText("Rechercher un écran, un client, une vente, une action…");

        HBox inputWrap = new HBox(10, Icons.feather("search", 18), input, Keycap.of("ESC"));
        inputWrap.getStyleClass().add("palette-input-wrap");
        inputWrap.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(input, Priority.ALWAYS);

        ObservableList<PaletteEntry> all = FXCollections.observableArrayList(buildPaletteEntries());
        FilteredList<PaletteEntry> filtered = new FilteredList<>(all, e -> true);

        ListView<PaletteEntry> list = new ListView<>(filtered);
        list.getStyleClass().add("palette-list");
        list.setFixedCellSize(42);
        list.setPrefHeight(300);
        list.setCellFactory(v -> new PaletteCell());

        Label empty = new Label("Aucun résultat");
        empty.getStyleClass().add("palette-item-hint");

        input.textProperty().addListener((obs, old, value) -> {
            String q = Format.normalize(value);
            filtered.setPredicate(e -> q.isBlank()
                    || Format.normalize(e.label()).contains(q)
                    || Format.normalize(e.hint()).contains(q));
            if (!filtered.isEmpty()) {
                list.getSelectionModel().select(0);
            }
        });

        HBox footer = new HBox(Keycap.hint("Naviguer", "↑ ↓"), Keycap.hint("Ouvrir", "↵"),
                Keycap.hint("Fermer", "ESC"));
        footer.getStyleClass().add("palette-footer");
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(inputWrap, list, footer);
        card.getStyleClass().add("palette-card");

        DialogService.ModalHandle handle = DialogService.get().custom(card, true);

        input.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case DOWN -> {
                    list.getSelectionModel().selectNext();
                    e.consume();
                }
                case UP -> {
                    list.getSelectionModel().selectPrevious();
                    e.consume();
                }
                case ENTER -> {
                    PaletteEntry entry = list.getSelectionModel().getSelectedItem();
                    if (entry != null) {
                        handle.close();
                        entry.action().run();
                    }
                    e.consume();
                }
                default -> {
                }
            }
        });
        list.setOnMouseClicked(e -> {
            PaletteEntry entry = list.getSelectionModel().getSelectedItem();
            if (entry != null) {
                handle.close();
                entry.action().run();
            }
        });

        javafx.application.Platform.runLater(input::requestFocus);
        if (!filtered.isEmpty()) {
            list.getSelectionModel().select(0);
        }
    }

    /** Entrées de la palette : écrans du menu + actions rapides (SPEC §2). */
    private List<PaletteEntry> buildPaletteEntries() {
        List<PaletteEntry> entries = new ArrayList<>();
        Role r = SessionContext.get().getRole();
        for (NavItem item : PermissionService.get().visibleItems(r)) {
            entries.add(new PaletteEntry(item.getTitle(), "Écran · " + sectionLabel(item.getSection()),
                    item.getIcon(), () -> NavigationService.get().navigate(item)));
        }

        // Recherche dynamique : Produits, Clients, Ventes, Réparations
        try {
            com.techshop.dao.ProductDao pdao = new com.techshop.dao.ProductDao();
            for (var pr : pdao.search(com.techshop.dao.ProductDao.Filter.all(), "name", true, 6, 0)) {
                entries.add(new PaletteEntry(pr.getName(), "Produit · " + pr.getRef() + " · " + com.techshop.util.Format.money(pr.getSalePrice()),
                        "box", () -> ouvrirModule("products")));
            }
            com.techshop.dao.CustomerDao cdao = new com.techshop.dao.CustomerDao();
            for (var c : cdao.search(new com.techshop.dao.CustomerDao.Filter("", null, null))) {
                String hint = "Client · " + (c.getPhone() == null ? "—" : c.getPhone()) + " · Solde " + com.techshop.util.Format.money(c.getBalance());
                entries.add(new PaletteEntry(c.getFullName(), hint, "users", () -> ouvrirModule("customers")));
            }
            com.techshop.service.SaleService ss = com.techshop.service.SaleService.get();
            for (var s : ss.history(new com.techshop.dao.SaleDao.Filter(null, null, null, null, null, null, null), 0, 6).items()) {
                entries.add(new PaletteEntry(s.getNumber(), "Vente · " + com.techshop.util.Format.date(s.getSaleDate().toLocalDate()) + " · " + com.techshop.util.Format.money(s.getTotal()),
                        "shopping-bag", () -> ouvrirModule("sales")));
            }
            com.techshop.dao.RepairTicketDao rdao = new com.techshop.dao.RepairTicketDao();
            for (var rt : rdao.search(null, 6, 0)) {
                entries.add(new PaletteEntry(rt.getNumber(), "SAV · " + rt.deviceLabel() + " · " + rt.customerLabel(),
                        "tool", () -> com.techshop.controller.RepairsController.openTicket(rt.getId())));
            }
        } catch (Exception ex) {
            log.warn("Palette : recherche dynamique impossible", ex);
        }

        entries.add(new PaletteEntry("Nouvelle vente", "Action rapide", "shopping-cart",
                () -> ouvrirModule("pos")));
        entries.add(new PaletteEntry("Nouvelle réparation", "Action rapide", "tool",
                () -> com.techshop.controller.RepairDepositDialog.ouvrir(null)));
        entries.add(new PaletteEntry("Ajouter un produit", "Catalogue", "plus-circle",
                () -> ouvrirModule("products")));
        entries.add(new PaletteEntry("Réception de stock", "Catalogue & stock", "download",
                () -> ouvrirModule("stock")));
        entries.add(new PaletteEntry(ThemeManager.isDark() ? "Passer au thème clair" : "Passer au thème sombre",
                "Apparence", ThemeManager.isDark() ? "sun" : "moon", this::toggleTheme));
        return entries;
    }

    private record PaletteEntry(String label, String hint, String icon, Runnable action) {
    }

    /** Cellule de la palette : icône + libellé + précision. */
    private static final class PaletteCell extends ListCell<PaletteEntry> {
        @Override
        protected void updateItem(PaletteEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label label = new Label(entry.label());
            label.getStyleClass().add("palette-item-label");
            Label hint = new Label(entry.hint());
            hint.getStyleClass().add("palette-item-hint");
            VBox texts = new VBox(0, label, hint);
            HBox box = new HBox(10, Icons.feather(entry.icon(), 16), texts);
            box.getStyleClass().add("palette-item");
            box.setAlignment(Pos.CENTER_LEFT);
            setText(null);
            setGraphic(box);
        }
    }

    /** Mémorise les liens externes pour d'éventuelles extensions (menu « Aide »). */
    private final Map<String, Runnable> quickActions = new LinkedHashMap<>();
}
