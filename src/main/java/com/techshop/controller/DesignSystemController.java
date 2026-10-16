package com.techshop.controller;

import com.techshop.config.ThemeManager;
import com.techshop.ui.Icons;
import com.techshop.ui.components.Avatar;
import com.techshop.ui.components.Badge;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.EmptyState;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.ui.components.Keycap;
import com.techshop.ui.components.StatCard;
import com.techshop.ui.components.TableFooter;
import com.techshop.ui.components.ToggleSwitch;
import com.techshop.ui.components.ToastService;
import com.techshop.util.Format;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Page « Design system » (SPEC §4.13) : présente tous les composants
 * réutilisables du package ui pour valider le rendu dans les deux thèmes.
 */
public class DesignSystemController {

    @FXML
    private VBox content;

    /** Palette des jetons affichés (nom, hex, description). */
    private record Swatch(String nom, String hex) {
    }

    private static final List<Swatch> LIGHT = List.of(
            new Swatch("fond", "#F8FAFC"), new Swatch("surface", "#FFFFFF"),
            new Swatch("surface-2", "#F1F5F9"), new Swatch("bordure", "#E2E8F0"),
            new Swatch("texte", "#0F172A"), new Swatch("texte-2", "#64748B"),
            new Swatch("primaire", "#4F46E5"), new Swatch("primaire-survolé", "#4338CA"),
            new Swatch("primaire-léger", "#EEF2FF"), new Swatch("succès", "#16A34A"),
            new Swatch("succès-fond", "#DCFCE7"), new Swatch("alerte", "#F59E0B"),
            new Swatch("alerte-fond", "#FEF3C7"), new Swatch("erreur", "#DC2626"),
            new Swatch("erreur-fond", "#FEE2E2"), new Swatch("info", "#2563EB"),
            new Swatch("info-fond", "#DBEAFE"));

    private static final List<Swatch> DARK = List.of(
            new Swatch("fond", "#0B1120"), new Swatch("surface", "#111C2E"),
            new Swatch("surface-2", "#1E293B"), new Swatch("bordure", "#263449"),
            new Swatch("texte", "#F1F5F9"), new Swatch("texte-2", "#94A3B8"),
            new Swatch("primaire", "#6366F1"), new Swatch("primaire-survolé", "#4F46E5"),
            new Swatch("primaire-léger", "rgba(99,102,241,.16)"), new Swatch("succès", "#4ADE80"),
            new Swatch("succès-fond", "rgba(74,222,128,.14)"), new Swatch("alerte", "#FBBF24"),
            new Swatch("alerte-fond", "rgba(251,191,36,.14)"), new Swatch("erreur", "#F87171"),
            new Swatch("erreur-fond", "rgba(248,113,113,.14)"), new Swatch("info", "#60A5FA"),
            new Swatch("info-fond", "rgba(96,165,250,.14)"));

    @FXML
    private void initialize() {
        content.getChildren().addAll(
                intro(),
                sectionCouleurs(),
                sectionTypographie(),
                sectionBoutons(),
                sectionChamps(),
                sectionBadgesEtCartes(),
                sectionTableau(),
                sectionOngletsEtControles(),
                sectionDialoguesEtToasts(),
                sectionEtats());
    }

    // ------------------------------------------------------------------- sections

    private Node intro() {
        Label title = new Label("Design system TechShop Manager");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label sub = new Label("Composants réutilisables du package ui — "
                + "vérifiez le rendu dans les deux thèmes avec la bascule de la barre supérieure.");
        sub.getStyleClass().add("muted");
        VBox box = new VBox(4, title, sub);
        box.setPadding(new Insets(0, 0, 8, 0));
        return box;
    }

    private Node sectionCouleurs() {
        List<Swatch> swatches = ThemeManager.isDark() ? DARK : LIGHT;
        FlowPane pane = new FlowPane(12, 12);
        for (Swatch s : swatches) {
            Region color = new Region();
            color.setPrefSize(120, 44);
            color.setStyle("-fx-background-color: " + s.hex() + "; -fx-background-radius: 8; "
                    + "-fx-border-color: #E2E8F0; -fx-border-radius: 8;");
            Label name = new Label(s.nom());
            name.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
            Label hex = new Label(s.hex());
            hex.getStyleClass().add("muted");
            hex.setStyle("-fx-font-size: 10px;");
            pane.getChildren().add(new VBox(4, color, name, hex));
        }
        return section("Couleurs (" + (ThemeManager.isDark() ? "thème sombre" : "thème clair") + ")",
                "Jetons du design system", pane);
    }

    private Node sectionTypographie() {
        VBox box = new VBox(10,
                styled("Titre de page — 24 px semi-gras", "-fx-font-size: 24px; -fx-font-weight: bold;"),
                styled("Sous-titre — 14 px", "-fx-font-size: 14px;"),
                styled("LIBELLÉ 12 PX MAJUSCULES ESPACÉES",
                        "-fx-font-size: 12px; -fx-font-weight: bold; -fx-letter-spacing: 1px;"),
                styled("Corps de texte 13 px — utilisé par défaut dans l’application.",
                        "-fx-font-size: 13px;"),
                styled("Grand chiffre : " + Format.money(new BigDecimal("12500")),
                        "-fx-font-size: 30px; -fx-font-weight: bold;"),
                styled("Montant tabulaire : " + Format.money(new BigDecimal("987654.5")),
                        "-fx-font-size: 14px;"));
        return section("Typographie", "Inter (repli Segoe UI), grille de 8 px", box);
    }

    private Node sectionBoutons() {
        HBox variants = new HBox(8,
                Buttons.primary("Principal", "check"),
                Buttons.secondary("Secondaire"),
                Buttons.outline("Contour", "edit-2"),
                Buttons.danger("Danger", "trash-2"),
                Buttons.ghost("Fantôme"),
                Buttons.icon("settings", "Icône seule"),
                Buttons.ghostIcon("more-vertical", "Actions"));
        variants.setAlignment(Pos.CENTER_LEFT);

        var disabled = Buttons.primary("Désactivé");
        disabled.setDisable(true);
        var compact = Buttons.secondary("Compact 30 px");
        Buttons.compact(compact);
        var disabledIcon = Buttons.icon("bell", "Désactivé");
        disabledIcon.setDisable(true);

        HBox states = new HBox(8, compact, disabled, disabledIcon);
        states.setAlignment(Pos.CENTER_LEFT);

        HBox shortcuts = new HBox(16,
                Buttons.actionWithShortcut("Encaisser", "credit-card", "F10",
                        () -> ToastService.get().success("Caisse", "Encaissement (démo)"), "btn-primary"),
                Buttons.actionWithShortcut("Rechercher", "search", "F2",
                        () -> ToastService.get().info("Recherche", "Champ de recherche (démo)"), "btn-secondary"));
        shortcuts.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(12, variants, states,
                new Label("Avec raccourcis clavier") {{
                    getStyleClass().add("field-label");
                }}, shortcuts);
        // Astuce : focus visible — tabulez pour voir l'anneau indigo
        Label note = new Label("Astuce : utilisez Tab pour voir l’état « focus visible » (anneau indigo).");
        note.getStyleClass().add("muted");
        box.getChildren().add(note);
        return section("Boutons", "Principal, secondaire, contour, danger, fantôme, icône seule", box);
    }

    private Node sectionChamps() {
        FormField fText = Fields.text("Nom du produit", "Ex. Samsung Galaxy A15 128 Go");
        FormField fNum = Fields.number("Prix de vente (DA)", "0,00");
        FormField fPwd = Fields.password("Mot de passe");
        FormField fSearch = Fields.search("Rechercher un produit, un IMEI…");
        FormField fDate = Fields.date("Date prévue");
        FormField fCombo = Fields.combo("Catégorie",
                List.of("Téléphones", "Accessoires", "Petites pièces", "Outils informatiques"));
        FormField fArea = Fields.area("Description", "Caractéristiques, état…");

        FormField fError = Fields.text("Téléphone", "0X XX XX XX XX");
        fError.setError("Numéro invalide : 10 chiffres attendus.");

        FormField fDisabled = Fields.text("Référence (auto)", "Généré automatiquement");
        fDisabled.styledControl().setDisable(true);

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);
        grid.add(fText, 0, 0);
        grid.add(fNum, 1, 0);
        grid.add(fPwd, 0, 1);
        grid.add(fSearch, 1, 1);
        grid.add(fDate, 0, 2);
        grid.add(fCombo, 1, 2);
        grid.add(fError, 0, 3);
        grid.add(fDisabled, 1, 3);
        grid.add(fArea, 0, 4, 2, 1);
        for (Node n : grid.getChildren()) {
            if (n instanceof Region r) {
                r.setMaxWidth(Double.MAX_VALUE);
            }
        }
        javafx.scene.layout.ColumnConstraints c1 = new javafx.scene.layout.ColumnConstraints();
        c1.setPercentWidth(50);
        javafx.scene.layout.ColumnConstraints c2 = new javafx.scene.layout.ColumnConstraints();
        c2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(c1, c2);

        Label note = new Label("États : normal, focus (anneau indigo), erreur (bordure rouge + message), désactivé.");
        note.getStyleClass().add("muted");
        VBox box = new VBox(12, grid, note);
        return section("Champs", "Texte, nombre, mot de passe avec œil, recherche, date, liste, zone de texte", box);
    }

    private Node sectionBadgesEtCartes() {
        HBox badges = new HBox(8,
                Badge.of("Payée", Badge.BadgeType.SUCCESS),
                Badge.of("Partielle", Badge.BadgeType.WARNING),
                Badge.of("Impayée", Badge.BadgeType.DANGER),
                Badge.of("En réparation", Badge.BadgeType.INFO),
                Badge.of("Archivé", Badge.BadgeType.NEUTRAL));
        badges.setAlignment(Pos.CENTER_LEFT);

        TilePane cards = new TilePane(12, 12,
                StatCard.of("Ventes du jour", Format.money(new BigDecimal("84500")), new BigDecimal("12.4")),
                StatCard.of("Bénéfice du jour", Format.money(new BigDecimal("21250")), new BigDecimal("-3.1")),
                StatCard.of("Ticket moyen", Format.money(new BigDecimal("6035.71")), new BigDecimal("0")),
                new StatCard("Réparations en cours", "7", "2 prêtes", StatCard.Trend.NEUTRAL, false));
        cards.setPrefColumns(4);

        HBox avatars = new HBox(8,
                Avatar.of("Nadia Belkacem", 32),
                Avatar.of("Yacine Cherif", 32),
                Avatar.of("Amine Bouzid", 32),
                Avatar.of("Lila Haddad", 32),
                Avatar.of("Karim Meziane", 28));
        avatars.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(12, badges, cards,
                new Label("Avatars à initiales (couleur déterministe)") {{
                    getStyleClass().add("field-label");
                }}, avatars);
        return section("Badges & cartes statistiques", "Statuts, variations ▲ / ▼, mini-courbe", box);
    }

    private Node sectionTableau() {
        record Ligne(String ref, String nom, String categorie, int stock, BigDecimal prix, String statut,
                     boolean alerte) {
        }

        TableView<Ligne> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Ligne, String> cRef = new TableColumn<>("Référence");
        cRef.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().ref()));
        cRef.setPrefWidth(120);

        TableColumn<Ligne, String> cNom = new TableColumn<>("Produit");
        cNom.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().nom()));
        cNom.setPrefWidth(260);

        TableColumn<Ligne, String> cCat = new TableColumn<>("Catégorie");
        cCat.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().categorie()));

        TableColumn<Ligne, Integer> cStock = new TableColumn<>("Stock");
        cStock.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().stock()));
        cStock.setStyle("-fx-alignment: CENTER-RIGHT;");
        cStock.setPrefWidth(90);

        TableColumn<Ligne, String> cPrix = new TableColumn<>("Prix de vente");
        cPrix.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().prix())));
        cPrix.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
        cPrix.setPrefWidth(150);

        TableColumn<Ligne, String> cStatut = new TableColumn<>("Statut");
        cStatut.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().statut()));
        cStatut.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Badge.BadgeType type = switch (item) {
                    case "En stock" -> Badge.BadgeType.SUCCESS;
                    case "Stock faible" -> Badge.BadgeType.WARNING;
                    case "Rupture" -> Badge.BadgeType.DANGER;
                    default -> Badge.BadgeType.NEUTRAL;
                };
                setGraphic(Badge.of(item, type));
                setText(null);
            }
        });

        TableColumn<Ligne, Void> cActions = new TableColumn<>("");
        cActions.setPrefWidth(56);
        cActions.setStyle("-fx-alignment: CENTER;");
        cActions.setCellFactory(col -> new TableCell<>() {
            private final javafx.scene.control.Button menu = Buttons.ghostIcon("more-vertical", "Actions");

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : menu);
            }
        });

        table.getColumns().addAll(List.of(cRef, cNom, cCat, cStock, cPrix, cStatut, cActions));

        ObservableList<Ligne> data = FXCollections.observableArrayList(
                new Ligne("P-000101", "Samsung Galaxy A15 128 Go", "Téléphones", 12,
                        new BigDecimal("34500"), "En stock", false),
                new Ligne("P-000102", "Chargeur USB-C 25W", "Accessoires", 4,
                        new BigDecimal("2500"), "Stock faible", true),
                new Ligne("P-000103", "Écran OLED iPhone 13", "Petites pièces", 0,
                        new BigDecimal("18500"), "Rupture", true),
                new Ligne("P-000104", "Clavier mécanique AZERTY", "Outils informatiques", 7,
                        new BigDecimal("8900"), "En stock", false),
                new Ligne("P-000105", "Batterie Redmi Note 11", "Petites pièces", 15,
                        new BigDecimal("3200"), "En stock", false));
        table.setItems(data);

        // Lignes d'alerte : fond « alerte » (SPEC §2)
        table.setRowFactory(tv -> new javafx.scene.control.TableRow<>() {
            @Override
            protected void updateItem(Ligne item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("row-alert");
                if (!empty && item != null && item.alerte()) {
                    getStyleClass().add("row-alert");
                }
            }
        });

        TableFooter footer = new TableFooter();
        footer.setResultCount(data.size());
        footer.setPage(1, 1);

        VBox wrap = new VBox(table, footer);
        wrap.getStyleClass().add("table-wrap");
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        wrap.setPrefHeight(360);

        Label note = new Label("En-tête, lignes 44 px, survol, sélection indigo, ligne d’alerte, "
                + "menu d’actions par ligne, pagination et compteur de résultats.");
        note.getStyleClass().add("muted");
        VBox box = new VBox(12, wrap, note);
        return section("Tableau", "TableView stylé", box);
    }

    private Node sectionOngletsEtControles() {
        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        for (String t : List.of("Général", "Prix", "Stock", "Options IMEI / garantie")) {
            Tab tab = new Tab(t);
            Label l = new Label("Contenu de l’onglet « " + t + " »");
            l.getStyleClass().add("muted");
            VBox holder = new VBox(l);
            holder.setPadding(new Insets(8, 0, 0, 0));
            tab.setContent(holder);
            tabs.getTabs().add(tab);
        }

        ToggleSwitch sw1 = new ToggleSwitch();
        sw1.setSelected(true);
        ToggleSwitch sw2 = new ToggleSwitch();
        HBox switches = new HBox(16,
                new HBox(8, sw1, new Label("Suivi par IMEI")),
                new HBox(8, sw2, new Label("Impression automatique")));
        switches.setAlignment(Pos.CENTER_LEFT);

        CheckBox cb1 = new CheckBox("Afficher les produits archivés");
        CheckBox cb2 = new CheckBox("Autoriser la vente en dessous du seuil");
        cb2.setSelected(true);
        HBox checks = new HBox(16, cb1, cb2);
        checks.setAlignment(Pos.CENTER_LEFT);

        ToggleGroup chipsGroup = new ToggleGroup();
        HBox chips = new HBox(8);
        for (String c : List.of("Tous", "Téléphones", "Accessoires", "Petites pièces", "Outils")) {
            ToggleButton chip = new ToggleButton(c);
            chip.getStyleClass().add("chip");
            chip.setToggleGroup(chipsGroup);
            if (c.equals("Tous")) {
                chip.setSelected(true);
            }
            chips.getChildren().add(chip);
        }
        chips.setAlignment(Pos.CENTER_LEFT);

        Label note = new Label("Onglets soulignés, interrupteurs, cases à cocher, puces de filtre sélectionnables.");
        note.getStyleClass().add("muted");

        VBox box = new VBox(16, tabs, switches, checks, chips, note);
        return section("Onglets & contrôles", "Navigation interne et sélections", box);
    }

    private Node sectionDialoguesEtToasts() {
        var confirmBtn = Buttons.danger("Confirmation…", "alert-triangle");
        confirmBtn.setOnAction(e -> DialogService.get()
                .confirm("Supprimer le produit",
                        "Cette action est irréversible. Le produit et son historique de stock "
                                + "seront désactivés.", "Supprimer", true)
                .thenAccept(ok -> ToastService.get().show(
                        Boolean.TRUE.equals(ok) ? ToastService.ToastType.SUCCESS : ToastService.ToastType.INFO,
                        "Dialogue", Boolean.TRUE.equals(ok) ? "Action confirmée (démo)" : "Action annulée")));

        var formBtn = Buttons.primary("Formulaire…", "edit-2");
        formBtn.setOnAction(e -> {
            FormField nom = Fields.text("Nom de la catégorie", "Ex. Accessoires");
            FormField seuil = Fields.number("Seuil d’alerte par défaut", "5");
            VBox body = new VBox(12, nom, seuil);
            body.setPrefWidth(420);
            DialogService.get().form("Nouvelle catégorie", body, "Enregistrer")
                    .thenAccept(ok -> ToastService.get().success("Formulaire",
                            Boolean.TRUE.equals(ok) ? "Catégorie enregistrée (démo)" : "Saisie abandonnée"));
        });

        var toastOk = Buttons.secondary("Toast succès", "check-circle");
        toastOk.setOnAction(e -> ToastService.get().success("Vente enregistrée",
                "V-2026-000123 — 34 500,00 DA encaissés en espèces."));
        var toastErr = Buttons.secondary("Toast erreur", "x-circle");
        toastErr.setOnAction(e -> ToastService.get().error("Stock insuffisant",
                "Impossible de vendre 3 unités : il reste 1 exemplaire."));
        var toastInfo = Buttons.secondary("Toast info", "info");
        toastInfo.setOnAction(e -> ToastService.get().info("Sauvegarde terminée",
                "La base de données a été exportée dans sauvegardes/techshop.sql."));

        HBox box = new HBox(8, confirmBtn, formBtn, toastOk, toastErr, toastInfo);
        box.setAlignment(Pos.CENTER_LEFT);
        return section("Dialogues & notifications", "Modales (voile assombri, rayon 16) et toasts haut-droite", box);
    }

    private Node sectionEtats() {
        EmptyState empty = EmptyState.of("package",
                "Aucun produit dans cette catégorie",
                "Ajoutez votre premier produit ou importez un catalogue CSV.")
                .withAction("Ajouter un produit", () -> ToastService.get().info("Produits",
                        "Le module Produits arrive dans une prochaine phase"));

        VBox loading = new VBox(com.techshop.ui.components.LoadingIndicator.withLabel(
                "Chargement des données…"));
        loading.getStyleClass().add("loading-box");

        HBox box = new HBox(24, cardOf(empty), cardOf(loading));
        HBox.setHgrow(box.getChildren().get(0), javafx.scene.layout.Priority.ALWAYS);
        return section("États vides & chargement", "Icône + message + action, indicateur de chargement", box);
    }

    // -------------------------------------------------------------------- helpers

    private Node cardOf(Node inner) {
        VBox card = new VBox(inner);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);
        HBox.setHgrow(card, javafx.scene.layout.Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private Label styled(String text, String style) {
        Label l = new Label(text);
        l.setStyle(style);
        return l;
    }

    /** Section : titre + sous-titre + contenu dans une carte. */
    private Node section(String titre, String sousTitre, Node contenu) {
        Label t = new Label(titre);
        t.getStyleClass().add("section-title");
        t.setPadding(new Insets(16, 0, 0, 0));
        Label s = new Label(sousTitre);
        s.getStyleClass().add("muted");

        VBox card = new VBox(14, contenu);
        card.getStyleClass().add("card");

        VBox box = new VBox(4, t, s, card);
        box.setPadding(new Insets(0, 0, 12, 0));
        return box;
    }
}
