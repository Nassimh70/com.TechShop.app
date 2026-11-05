package com.techshop.app;

import com.techshop.config.DatabaseManager;
import com.techshop.config.ThemeManager;
import com.techshop.controller.MainController;
import com.techshop.license.LicenseScreen;
import com.techshop.license.LicenseService;
import com.techshop.service.BackupService;
import com.techshop.service.SetupService;
import com.techshop.ui.Branding;
import com.techshop.ui.components.LoadingIndicator;
import com.techshop.util.Fonts;
import com.techshop.util.Fxmls;
import com.techshop.util.Loaded;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coque de l'application (SPEC §1) : une seule fenêtre, une seule scène dont
 * on remplace la racine selon l'étape — démarrage (préparation de la base),
 * connexion, changement de mot de passe obligatoire, erreur de connexion,
 * fenêtre principale. Centralise le flux de démarrage et la déconnexion.
 */
public final class AppShell {

    private static final Logger log = LoggerFactory.getLogger(AppShell.class);

    private static Stage stage;
    private static Scene scene;
    private static String fontFamily = Fonts.FALLBACK;

    private AppShell() {
    }

    /** Prépare la fenêtre puis lance le flux de démarrage (SPEC §7). */
    public static void start(Stage stageApp) {
        stage = stageApp;
        fontFamily = Fonts.install();

        scene = new Scene(new StackPane(), 1366, 768);
        ThemeManager.apply(scene);

        stage.setTitle("TechShop Manager");
        Branding.applyIcons(stage);
        stage.setMinWidth(1180);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.show();

        checkLicense();
    }

    /**
     * Vérifie la licence hors ligne avant toute ouverture de la base.
     * Un poste sans licence valide reste bloqué sur l'écran d'activation.
     */
    private static void checkLicense() {
        LicenseService.LicenseStatus status;
        try {
            status = LicenseService.current();
        } catch (RuntimeException e) {
            log.error("Vérification de la licence impossible", e);
            status = null;
        }
        if (status != null && status.isAccepted()) {
            if (status.state() == LicenseService.State.EXPIRING_SOON) {
                log.warn("Licence {} : {}", status.activityLabel(), status.message());
            }
            bootstrap();
            return;
        }
        showLicense(status);
    }

    /** Affiche l'écran d'activation (licence absente, expirée ou invalide). */
    public static void showLicense() {
        showLicense(LicenseService.current());
    }

    private static void showLicense(LicenseService.LicenseStatus status) {
        setRoot(LicenseScreen.create(status, AppShell::bootstrap));
    }

    /** Initialise la base en tâche de fond puis affiche l'écran approprié. */
    public static void bootstrap() {
        showStartup();
        Thread t = new Thread(() -> {
            DatabaseManager.init();
            Platform.runLater(() -> {
                if (DatabaseManager.isReady()) {
                    if (SetupService.get().isSetupRequired()) {
                        showSetup();
                    } else {
                        showLogin();
                    }
                } else {
                    showDbError();
                }
            });
        }, "db-bootstrap");
        t.setDaemon(true);
        t.start();
    }

    /** Bouton « Réessayer » de l'écran d'erreur de connexion. */
    public static void retry() {
        bootstrap();
    }

    // ------------------------------------------------------------- écrans

    public static void showStartup() {
        LoadingIndicator spinner = new LoadingIndicator(42);
        Label titre = new Label("TechShop Manager");
        titre.getStyleClass().add("splash-title");
        Label etat = new Label("Préparation de la base de données…");
        etat.getStyleClass().add("splash-state");

        VBox box = new VBox(18, Branding.mark(72), titre, spinner, etat);
        box.setAlignment(Pos.CENTER);

        StackPane root = new StackPane(box);
        root.getStyleClass().addAll("app-root", "splash");
        setRoot(root);
    }

    public static void showLogin() {
        setRoot(load("screens/login"));
    }

    public static void showSetup() {
        setRoot(load("screens/setup"));
    }

    /** Écran de changement de mot de passe obligatoire (admin initial). */
    public static void showChangePassword() {
        setRoot(load("screens/change-password"));
    }

    /** Écran d'erreur de connexion à la base (SPEC §5, §7). */
    public static void showDbError() {
        setRoot(load("screens/db-error"));
    }

    /** Fenêtre principale (après authentification). */
    public static void showMain() {
        Loaded loaded = Fxmls.load("main");
        Parent root = loaded.root();
        setRoot(root);
        if (loaded.controller() instanceof MainController controller) {
            controller.start();
        }
    }

    // ------------------------------------------------------------- utilitaires

    private static Parent load(String fxml) {
        return Fxmls.load(fxml).root();
    }

    private static void setRoot(Parent root) {
        root.setStyle("-fx-font-family: \"" + fontFamily + "\";");
        scene.setRoot(root);
        log.debug("Racine affichée : {}", root.getClass().getSimpleName());
    }

    /** Ferme proprement la base (arrêt de l'application). */
    public static void stop() {
        try {
            BackupService.get().autoBackupOnExit();
        } catch (Exception e) {
            log.warn("Sauvegarde automatique impossible : {}", e.getMessage());
        }
        DatabaseManager.shutdown();
    }
}
