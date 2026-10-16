package com.techshop.controller;

import com.techshop.dao.ActivityLogDao;
import com.techshop.model.ActivityLog;
import com.techshop.model.Permission;
import com.techshop.model.Role;
import com.techshop.model.User;
import com.techshop.service.ActivityLogService;
import com.techshop.service.PermissionService;
import com.techshop.service.UserService;
import com.techshop.ui.Icons;
import com.techshop.ui.components.Avatar;
import com.techshop.ui.components.Badge;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.ui.components.TableFooter;
import com.techshop.ui.components.ToastService;
import com.techshop.util.Format;
import com.techshop.util.ValidationException;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Module Utilisateurs (SPEC §3, §4.12, §6) : liste et gestion des comptes,
 * matrice de permissions par rôle et journal d'activité filtrable.
 * Aucune règle métier ici : tout passe par {@link UserService} et
 * {@link ActivityLogService}.
 */
public class UsersController {

    @FXML
    private VBox usersTab;
    @FXML
    private VBox permsTab;
    @FXML
    private VBox journalTab;
    @FXML
    private Button newUserBtn;

    private final UserService userService = UserService.get();
    private final ActivityLogService journal = ActivityLogService.get();
    private final PermissionService permissions = PermissionService.get();

    // --- Comptes ---
    private final ObservableList<User> donneesUtilisateurs = FXCollections.observableArrayList();
    private TableView<User> tableUtilisateurs;
    private TextField champRecherche;
    private ComboBox<String> filtreRole;
    private ComboBox<String> filtreStatut;
    private TableFooter piedUtilisateurs;

    // --- Permissions ---
    private ComboBox<Role> comboRole;
    private GridPane grillePermissions;
    private final Map<String, Map<PermissionService.Action, CheckBox>> casesPermissions =
            new LinkedHashMap<>();

    // --- Journal ---
    private final ObservableList<ActivityLog> donneesJournal = FXCollections.observableArrayList();
    private TableView<ActivityLog> tableJournal;
    private ComboBox<ActivityLogDao.UtilisateurOption> filtreJournalUtilisateur;
    private ComboBox<String> filtreJournalAction;
    private TextField filtreJournalTexte;
    private DatePicker filtreJournalDu;
    private DatePicker filtreJournalAu;

    @FXML
    private void initialize() {
        newUserBtn.setGraphic(Icons.feather("user-plus", 16));
        newUserBtn.setOnAction(e -> ouvrirFormulaire(null));
        construireOngletUtilisateurs();
        construireOngletPermissions();
        construireOngletJournal();
        chargerUtilisateurs();
    }

    // ============================================================ COMPTES

    private void construireOngletUtilisateurs() {
        champRecherche = new TextField();
        champRecherche.getStyleClass().add("field");
        champRecherche.setPromptText("Rechercher un nom, un identifiant…");
        champRecherche.setPrefWidth(280);
        champRecherche.textProperty().addListener((o, a, b) -> chargerUtilisateurs());

        filtreRole = new ComboBox<>(FXCollections.observableArrayList(
                "Tous les rôles", "Administrateur", "Vendeur", "Technicien"));
        filtreRole.getStyleClass().add("field");
        filtreRole.getSelectionModel().selectFirst();

        filtreStatut = new ComboBox<>(FXCollections.observableArrayList(
                "Tous les statuts", "Actifs", "Inactifs"));
        filtreStatut.getStyleClass().add("field");
        filtreStatut.getSelectionModel().selectFirst();

        Button rafraichir = Buttons.outline("Rafraîchir", "refresh-cw");
        rafraichir.setOnAction(e -> chargerUtilisateurs());

        HBox barre = new HBox(10, champRecherche, filtreRole, filtreStatut, rafraichir);
        barre.setAlignment(Pos.CENTER_LEFT);

        filtreRole.setOnAction(e -> chargerUtilisateurs());
        filtreStatut.setOnAction(e -> chargerUtilisateurs());

        tableUtilisateurs = new TableView<>(donneesUtilisateurs);
        tableUtilisateurs.getStyleClass().add("data-table");
        tableUtilisateurs.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(tableUtilisateurs, Priority.ALWAYS);
        construireColonnesUtilisateurs();

        piedUtilisateurs = new TableFooter();

        VBox carte = new VBox(tableUtilisateurs, piedUtilisateurs);
        carte.getStyleClass().add("table-wrap");
        VBox.setVgrow(carte, Priority.ALWAYS);

        usersTab.getChildren().setAll(barre, carte);
    }

    private void construireColonnesUtilisateurs() {
        TableColumn<User, User> cUtilisateur = new TableColumn<>("Utilisateur");
        cUtilisateur.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cUtilisateur.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                setText(null);
                if (empty || u == null) {
                    setGraphic(null);
                    return;
                }
                Label nom = new Label(u.getFullName());
                nom.getStyleClass().add("user-fullname");
                Label identifiant = new Label("@" + u.getUsername());
                identifiant.getStyleClass().add("muted");
                VBox textes = new VBox(0, nom, identifiant);
                HBox box = new HBox(10, Avatar.of(u.getFullName(), 30), textes);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });

        TableColumn<User, String> cRole = colonne("Rôle", u -> u.getRole().getLabel());
        cRole.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                setGraphic(empty || value == null ? null
                        : Badge.of(value, value.startsWith("Admin")
                                ? Badge.BadgeType.INFO : Badge.BadgeType.NEUTRAL));
            }
        });

        TableColumn<User, User> cStatut = new TableColumn<>("Statut");
        cStatut.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue()));
        cStatut.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                setText(null);
                setGraphic(empty || u == null ? null
                        : Badge.of(u.isActive() ? "Actif" : "Inactif",
                        u.isActive() ? Badge.BadgeType.SUCCESS : Badge.BadgeType.DANGER));
            }
        });

        TableColumn<User, String> cDerniere = colonne("Dernière connexion",
                u -> Format.dateTime(u.getLastLogin()));

        TableColumn<User, User> cActions = new TableColumn<>("Actions");
        cActions.setPrefWidth(160);
        cActions.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue()));
        cActions.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                setText(null);
                if (empty || u == null) {
                    setGraphic(null);
                    return;
                }
                Button modifier = Buttons.iconCompact("edit-2", "Modifier");
                modifier.setOnAction(e -> ouvrirFormulaire(u));

                Button basculer = Buttons.iconCompact(u.isActive() ? "user-x" : "user-check",
                        u.isActive() ? "Désactiver" : "Activer");
                basculer.setOnAction(e -> basculerStatut(u));

                Button reinit = Buttons.iconCompact("key", "Réinitialiser le mot de passe");
                reinit.setOnAction(e -> reinitialiserMotDePasse(u));

                HBox actions = new HBox(6, modifier, basculer, reinit);
                actions.setAlignment(Pos.CENTER_LEFT);
                setGraphic(actions);
            }
        });

        tableUtilisateurs.getColumns().setAll(
                List.of(cUtilisateur, cRole, cStatut, cDerniere, cActions));
        cUtilisateur.setPrefWidth(280);
        cRole.setMinWidth(120);
        cStatut.setMinWidth(100);
        cDerniere.setMinWidth(160);
    }

    private <T> TableColumn<User, T> colonne(String titre,
                                             java.util.function.Function<User, T> extracteur) {
        TableColumn<User, T> col = new TableColumn<>(titre);
        col.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(extracteur.apply(v.getValue())));
        return col;
    }

    private void chargerUtilisateurs() {
        try {
            Role role = roleDepuisFiltre(filtreRole == null ? null : filtreRole.getValue());
            Boolean actif = statutDepuisFiltre(filtreStatut == null ? null : filtreStatut.getValue());
            String texte = champRecherche == null ? null : champRecherche.getText();
            List<User> liste = userService.list(texte, role, actif);
            donneesUtilisateurs.setAll(liste);
            if (piedUtilisateurs != null) {
                piedUtilisateurs.setResultCount(donneesUtilisateurs.size());
            }
        } catch (ValidationException e) {
            ToastService.get().error("Utilisateurs", e.getMessage());
        }
    }

    private Button boutonAction(String icone, String infobulle, Runnable action) {
        Button b = Buttons.iconCompact(icone, infobulle);
        b.setOnAction(e -> action.run());
        return b;
    }

    // ============================================================ PERMISSIONS
    private void construireOngletPermissions() {
        Label intro = new Label("Cochez les droits accordés à chaque rôle, module par module. "
                + "Les restrictions sont appliquées dans les services, pas seulement dans l'interface.");
        intro.getStyleClass().add("muted");
        intro.setWrapText(true);

        comboRole = new ComboBox<>(FXCollections.observableArrayList(Role.values()));
        comboRole.getStyleClass().add("field");
        comboRole.setButtonCell(celluleRole());
        comboRole.setCellFactory(c -> celluleRole());
        comboRole.getSelectionModel().select(Role.VENDEUR);
        comboRole.setOnAction(e -> chargerPermissions());

        Button enregistrer = Buttons.primary("Enregistrer les permissions", "save");
        enregistrer.setOnAction(e -> sauvegarderPermissions());

        HBox barre = new HBox(10, new Label("Rôle :"), comboRole, new Region(), enregistrer);
        HBox.setHgrow(barre.getChildren().get(2), Priority.ALWAYS);
        barre.setAlignment(Pos.CENTER_LEFT);

        grillePermissions = new GridPane();
        grillePermissions.getStyleClass().addAll("perm-grid", "card");
        grillePermissions.setHgap(10);
        grillePermissions.setVgap(6);

        VBox carte = new VBox(grillePermissions);
        carte.getStyleClass().add("table-wrap");

        permsTab.getChildren().setAll(intro, barre, carte);
        chargerPermissions();
    }

    private ListCell<Role> celluleRole() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Role role, boolean empty) {
                super.updateItem(role, empty);
                setText(empty || role == null ? null : role.getLabel());
            }
        };
    }

    /** Libellés lisibles des modules de la matrice. */
    private static String libelleModule(String module) {
        return switch (module) {
            case "dashboard" -> "Tableau de bord";
            case "pos" -> "Point de vente";
            case "sales" -> "Ventes";
            case "customers" -> "Clients";
            case "products" -> "Produits";
            case "stock" -> "Stock";
            case "purchases" -> "Achats & fournisseurs";
            case "repairs" -> "SAV / Réparations";
            case "cash" -> "Caisse & finances";
            case "reports" -> "Rapports";
            case "users" -> "Utilisateurs";
            case "settings" -> "Paramètres";
            default -> module;
        };
    }

    private void chargerPermissions() {
        Role role = comboRole.getValue();
        if (role == null) {
            return;
        }
        Map<String, Permission> droits = userService.permissionsOf(role);
        grillePermissions.getChildren().clear();
        casesPermissions.clear();

        // En-tête
        grillePermissions.add(entete("Module"), 0, 0);
        PermissionService.Action[] actions = PermissionService.Action.values();
        for (int i = 0; i < actions.length; i++) {
            grillePermissions.add(entete(libelleAction(actions[i])), i + 1, 0);
        }

        List<String> modules = permissions.modules();
        int ligne = 1;
        for (String module : modules) {
            Label nom = new Label(libelleModule(module));
            nom.getStyleClass().add("perm-module");
            grillePermissions.add(nom, 0, ligne);

            Permission p = droits.get(module);
            Map<PermissionService.Action, CheckBox> cases = new LinkedHashMap<>();
            for (int i = 0; i < actions.length; i++) {
                CheckBox cb = new CheckBox();
                cb.setSelected(estAccorde(p, actions[i]));
                grillePermissions.add(cb, i + 1, ligne);
                cases.put(actions[i], cb);
            }
            casesPermissions.put(module, cases);
            ligne++;
        }
    }

    private static boolean estAccorde(Permission p, PermissionService.Action action) {
        if (p == null) {
            return false;
        }
        return switch (action) {
            case VIEW -> p.canView();
            case CREATE -> p.canCreate();
            case EDIT -> p.canEdit();
            case DELETE -> p.canDelete();
            case VALIDATE -> p.canValidate();
        };
    }

    private static Label entete(String texte) {
        Label l = new Label(texte);
        l.getStyleClass().add("perm-head");
        return l;
    }

    private static String libelleAction(PermissionService.Action action) {
        return switch (action) {
            case VIEW -> "Voir";
            case CREATE -> "Créer";
            case EDIT -> "Modifier";
            case DELETE -> "Supprimer";
            case VALIDATE -> "Valider";
        };
    }

    private void sauvegarderPermissions() {
        Role role = comboRole.getValue();
        if (role == null) {
            return;
        }
        List<Permission> lignes = new ArrayList<>();
        for (Map.Entry<String, Map<PermissionService.Action, CheckBox>> e : casesPermissions.entrySet()) {
            Map<PermissionService.Action, CheckBox> c = e.getValue();
            lignes.add(new Permission(e.getKey(),
                    c.get(PermissionService.Action.VIEW).isSelected(),
                    c.get(PermissionService.Action.CREATE).isSelected(),
                    c.get(PermissionService.Action.EDIT).isSelected(),
                    c.get(PermissionService.Action.DELETE).isSelected(),
                    c.get(PermissionService.Action.VALIDATE).isSelected()));
        }
        try {
            userService.savePermissions(role, lignes);
            ToastService.get().success("Permissions",
                    "Permissions du rôle « " + role.getLabel() + " » enregistrées.");
        } catch (ValidationException ex) {
            ToastService.get().error("Permissions", ex.getMessage());
        }
    }

    // ============================================================ JOURNAL

    private void construireOngletJournal() {
        filtreJournalUtilisateur = new ComboBox<>();
        filtreJournalUtilisateur.getStyleClass().add("field");
        filtreJournalUtilisateur.setPrefWidth(220);
        filtreJournalUtilisateur.getItems().add(
                new ActivityLogDao.UtilisateurOption(0, "", "Tous les utilisateurs", Role.ADMIN));
        filtreJournalUtilisateur.getItems().addAll(journal.utilisateurs());
        filtreJournalUtilisateur.getSelectionModel().selectFirst();

        filtreJournalAction = new ComboBox<>();
        filtreJournalAction.getStyleClass().add("field");
        filtreJournalAction.getItems().add("Toutes les actions");
        filtreJournalAction.getItems().addAll(journal.actionsDistinctes());
        filtreJournalAction.getSelectionModel().selectFirst();

        filtreJournalTexte = new TextField();
        filtreJournalTexte.getStyleClass().add("field");
        filtreJournalTexte.setPromptText("Rechercher dans le détail…");
        filtreJournalTexte.setPrefWidth(220);

        filtreJournalDu = new DatePicker();
        filtreJournalDu.getStyleClass().add("field");
        filtreJournalDu.setPromptText("Du");
        filtreJournalAu = new DatePicker();
        filtreJournalAu.getStyleClass().add("field");
        filtreJournalAu.setPromptText("Au");

        Button filtrer = Buttons.primary("Filtrer", "filter");
        filtrer.setOnAction(e -> chargerJournal());
        Button rafraichir = Buttons.outline("Rafraîchir", "refresh-cw");
        rafraichir.setOnAction(e -> chargerJournal());

        HBox barre = new HBox(10, filtreJournalUtilisateur, filtreJournalAction,
                filtreJournalTexte, filtreJournalDu, filtreJournalAu, filtrer, rafraichir);
        barre.setAlignment(Pos.CENTER_LEFT);

        tableJournal = new TableView<>(donneesJournal);
        tableJournal.getStyleClass().add("data-table");
        tableJournal.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(tableJournal, Priority.ALWAYS);
        construireColonnesJournal();

        VBox carte = new VBox(tableJournal);
        carte.getStyleClass().add("table-wrap");
        VBox.setVgrow(carte, Priority.ALWAYS);

        journalTab.getChildren().setAll(barre, carte);
        chargerJournal();
    }

    private void construireColonnesJournal() {
        TableColumn<ActivityLog, String> cDate = colonneJournal("Date",
                a -> Format.dateTime(a.createdAt()));
        TableColumn<ActivityLog, String> cUtilisateur = colonneJournal("Utilisateur",
                a -> a.username() == null ? "—" : a.username());
        TableColumn<ActivityLog, String> cAction = colonneJournal("Action",
                ActivityLog::actionLabel);
        TableColumn<ActivityLog, String> cEntite = colonneJournal("Entité",
                a -> a.entity() == null ? "—"
                        : a.entity() + (a.entityId() == null ? "" : " #" + a.entityId()));
        TableColumn<ActivityLog, String> cDetail = colonneJournal("Détail",
                a -> a.details() == null ? "" : a.details());

        tableJournal.getColumns().setAll(List.of(cUtilisateur, cAction, cEntite, cDetail));
        cUtilisateur.setPrefWidth(140);
        cAction.setPrefWidth(180);
        cEntite.setPrefWidth(140);
        cDetail.setPrefWidth(320);
    }

    private TableColumn<ActivityLog, String> colonneJournal(
            String titre, java.util.function.Function<ActivityLog, String> extracteur) {
        TableColumn<ActivityLog, String> col = new TableColumn<>(titre);
        col.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(extracteur.apply(v.getValue())));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String valeur, boolean empty) {
                super.updateItem(valeur, empty);
                setText(empty ? null : valeur);
            }
        });
        return col;
    }

    private void chargerJournal() {
        var opt = filtreJournalUtilisateur.getValue();
        Integer userId = opt == null || opt.id() == 0 ? null : opt.id();
        String action = filtreJournalAction.getValue();
        if (action != null && action.startsWith("Toutes")) {
            action = null;
        }
        String texte = filtreJournalTexte.getText();
        var du = filtreJournalDu.getValue();
        var au = filtreJournalAu.getValue();

        var filtre = new ActivityLogDao.Filter(userId, action, null, du, au, texte, 300);
        donneesJournal.setAll(journal.search(filtre));
    }

    // ============================================================ FORMULAIRE

    private void ouvrirFormulaire(User existant) {
        boolean creation = existant == null;

        FormField fNom = Fields.text("Nom complet", "ex. Nadia Belkacem");
        FormField fIdentifiant = Fields.text("Identifiant de connexion", "ex. n.belkacem");
        FormField fTelephone = Fields.text("Téléphone", "0X XX XX XX XX");
        FormField fEmail = Fields.text("Courriel", "nom@exemple.dz");

        ComboBox<Role> cRole = new ComboBox<>(FXCollections.observableArrayList(Role.values()));
        cRole.getStyleClass().add("field");
        cRole.setButtonCell(celluleRole());
        cRole.setCellFactory(c -> celluleRole());
        cRole.setMaxWidth(Double.MAX_VALUE);
        FormField fRole = new FormField("Rôle", cRole, cRole);

        FormField fMotDePasse = creation ? Fields.password("Mot de passe initial") : null;
        CheckBox forcermdp = new CheckBox("Exiger le changement de mot de passe à la connexion");
        forcermdp.setSelected(true);

        if (existant != null) {
            fNom.setValue(existant.getFullName());
            fIdentifiant.setValue(existant.getUsername());
            fIdentifiant.setDisable(true);
            fTelephone.setValue(existant.getPhone());
            fEmail.setValue(existant.getEmail());
            cRole.setValue(existant.getRole());
        } else {
            cRole.setValue(Role.VENDEUR);
        }

        Label erreur = new Label();
        erreur.getStyleClass().add("auth-error");
        erreur.setWrapText(true);
        erreur.setManaged(false);
        erreur.setVisible(false);

        VBox corps = new VBox(12, fNom, fIdentifiant, fTelephone, fEmail, fRole);
        if (fMotDePasse != null) {
            corps.getChildren().add(fMotDePasse);
            corps.getChildren().add(forcermdp);
        }
        corps.getChildren().add(erreur);
        corps.setPrefWidth(420);

        Button annuler = Buttons.secondary("Annuler");
        Button enregistrer = Buttons.primary(creation ? "Créer" : "Enregistrer", "check");
        HBox pied = new HBox(8, annuler, enregistrer);
        pied.getStyleClass().add("modal-footer");

        VBox carte = DialogService.get().card(
                creation ? "Nouvel utilisateur" : "Modifier l'utilisateur",
                creation ? "user-plus" : "edit-2", corps, pied);
        DialogService.ModalHandle handle = DialogService.get().custom(carte, true);

        final FormField motDePasse = fMotDePasse;
        Runnable enregistrerAction = () -> {
            erreur.setVisible(false);
            erreur.setManaged(false);
            try {
                if (creation) {
                    userService.create(fIdentifiant.textValue(), fNom.textValue(),
                            fTelephone.textValue(), fEmail.textValue(), cRole.getValue(),
                            motDePasse.textValue(), forcermdp.isSelected());
                    ToastService.get().success("Utilisateurs",
                            "Utilisateur « " + fIdentifiant.textValue() + " » créé.");
                } else {
                    userService.update(existant.getId(), fNom.textValue(),
                            fTelephone.textValue(), fEmail.textValue(),
                            cRole.getValue(), existant.isActive());
                    ToastService.get().success("Utilisateurs",
                            "Utilisateur « " + existant.getUsername() + " » mis à jour.");
                }
                handle.close();
                chargerUtilisateurs();
            } catch (ValidationException ex) {
                erreur.setText(ex.getMessage());
                erreur.setManaged(true);
                erreur.setVisible(true);
            }
        };

        annuler.setOnAction(e -> handle.close());
        enregistrer.setOnAction(e -> enregistrerAction.run());
        enregistrer.setDefaultButton(true);
    }

    private void basculerStatut(User u) {
        boolean desactiver = u.isActive();
        DialogService.get().confirm(
                        desactiver ? "Désactiver le compte" : "Activer le compte",
                        (desactiver ? "Désactiver" : "Activer") + " le compte de « "
                                + u.getFullName() + " » ?",
                        desactiver ? "Désactiver" : "Activer", desactiver)
                .thenAccept(ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        try {
                            userService.setActive(u.getId(), !desactiver);
                            ToastService.get().success("Utilisateurs",
                                    "Compte " + (desactiver ? "désactivé" : "activé") + ".");
                            chargerUtilisateurs();
                        } catch (ValidationException e) {
                            ToastService.get().error("Utilisateurs", e.getMessage());
                        }
                    }
                });
    }

    private void reinitialiserMotDePasse(User u) {
        DialogService.get().confirm("Réinitialiser le mot de passe",
                        "Un mot de passe temporaire sera généré pour « " + u.getFullName()
                                + " » ; le changement sera obligatoire à la prochaine connexion.",
                        "Réinitialiser", true)
                .thenAccept(ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        try {
                            String temporaire = userService.resetPassword(u.getId());
                            afficherMotDePasseTemporaire(u, temporaire);
                            chargerJournal();
                        } catch (ValidationException e) {
                            ToastService.get().error("Utilisateurs", e.getMessage());
                        }
                    }
                });
    }

    private void afficherMotDePasseTemporaire(User u, String temporaire) {
        Label intro = new Label("Communiquez ce mot de passe temporaire à « "
                + u.getFullName() + " ». Il devra le changer à la prochaine connexion.");
        intro.setWrapText(true);
        intro.setMaxWidth(400);

        Label mdp = new Label(temporaire);
        mdp.getStyleClass().add("temp-password");
        mdp.setMaxWidth(Double.MAX_VALUE);
        mdp.setAlignment(Pos.CENTER);

        Button copier = Buttons.outline("Copier", "copy");
        copier.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent contenu = new javafx.scene.input.ClipboardContent();
            contenu.putString(temporaire);
            clipboard.setContent(contenu);
            ToastService.get().info("Presse-papiers", "Mot de passe copié.");
        });

        VBox corps = new VBox(12, intro, mdp, copier);
        corps.setPrefWidth(400);
        DialogService.get().custom(
                DialogService.get().card("Mot de passe réinitialisé", "key", corps, null), true);
    }

    // ============================================================ UTILITAIRES

    private static Role roleDepuisFiltre(String libelle) {
        if (libelle == null) {
            return null;
        }
        return switch (libelle) {
            case "Administrateur" -> Role.ADMIN;
            case "Vendeur" -> Role.VENDEUR;
            case "Technicien" -> Role.TECHNICIEN;
            default -> null;
        };
    }

    private static Boolean statutDepuisFiltre(String libelle) {
        if (libelle == null) {
            return null;
        }
        return switch (libelle) {
            case "Actifs" -> Boolean.TRUE;
            case "Inactifs" -> Boolean.FALSE;
            default -> null;
        };
    }
}
