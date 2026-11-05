package com.techshop.license;

import com.techshop.ui.Branding;
import com.techshop.ui.components.Buttons;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Startup activation screen. The application cannot reach the database or the
 * login screen until a valid, locally signed licence is installed.
 */
public final class LicenseScreen {

    private LicenseScreen() {
    }

    public static Parent create(LicenseService.LicenseStatus initial, Runnable onActivated) {
        LicenseService.LicenseStatus status = initial == null
                ? LicenseService.current() : initial;

        // ------------------------------------------------------------- brand
        Label appName = new Label(LicenseService.PRODUCT_NAME);
        appName.getStyleClass().add("app-name");
        Label appSub = new Label("GESTION COMMERCIALE HORS LIGNE");
        appSub.getStyleClass().add("app-sub");

        HBox identity = new HBox(12, Branding.mark(48), new VBox(1, appName, appSub));
        identity.setAlignment(Pos.CENTER_LEFT);

        Label headline = new Label("Activez votre copie de TechShop Manager.");
        headline.getStyleClass().add("license-headline");
        headline.setWrapText(true);

        Label explanation = new Label("""
                La licence est vérifiée sur cet ordinateur, sans connexion Internet.
                Envoyez l'identifiant ci-contre à votre fournisseur, puis importez
                le fichier .lic qu'il vous retourne.""");
        explanation.getStyleClass().add("muted");
        explanation.setWrapText(true);

        Region brandTopSpacer = new Region();
        Region brandBottomSpacer = new Region();
        VBox brand = new VBox(22, identity, brandTopSpacer, headline, explanation, brandBottomSpacer);
        brand.getStyleClass().add("auth-brand");
        VBox.setVgrow(brandTopSpacer, Priority.ALWAYS);
        VBox.setVgrow(brandBottomSpacer, Priority.ALWAYS);

        // -------------------------------------------------------------- card
        Label title = new Label("Licence du logiciel");
        title.getStyleClass().add("auth-title");
        Label subtitle = new Label("Importez le fichier de licence fourni par votre revendeur.");
        subtitle.getStyleClass().add("auth-sub");
        subtitle.setWrapText(true);

        Label stateBadge = new Label(status.activityLabel());
        stateBadge.getStyleClass().addAll("badge", status.badgeStyle());

        Label stateMessage = new Label(status.message());
        stateMessage.getStyleClass().add(status.isAccepted() ? "muted" : "warning-text");
        stateMessage.setWrapText(true);

        VBox statusBox = new VBox(8, stateBadge, stateMessage);
        statusBox.getStyleClass().add("card-inset");
        statusBox.setStyle("-fx-padding: 12; -fx-background-radius: 10; -fx-border-radius: 10; -fx-border-width: 1;");

        Label deviceTitle = new Label("Identifiant de cet ordinateur");
        deviceTitle.getStyleClass().add("license-field-title");
        TextField deviceField = new TextField(LicenseService.deviceId());
        deviceField.setEditable(false);
        deviceField.setFocusTraversable(false);
        deviceField.getStyleClass().add("mono");
        deviceField.setStyle("-fx-font-size: 11px;");
        HBox.setHgrow(deviceField, Priority.ALWAYS);

        Label copyHint = new Label();
        copyHint.getStyleClass().add("muted");
        copyHint.setManaged(false);
        copyHint.setVisible(false);

        Button copyButton = Buttons.secondary("Copier", "copy");
        copyButton.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(deviceField.getText());
            Clipboard.getSystemClipboard().setContent(content);
            copyHint.setText("Identifiant copié dans le presse-papiers.");
            copyHint.setManaged(true);
            copyHint.setVisible(true);
        });

        HBox deviceRow = new HBox(8, deviceField, copyButton);
        deviceRow.setAlignment(Pos.CENTER_LEFT);

        Label deviceHint = new Label("""
                Cet identifiant ne change pas lors des mises à jour. Il est requis
                pour générer une licence valable sur ce poste.""");
        deviceHint.getStyleClass().add("muted");
        deviceHint.setWrapText(true);

        VBox deviceBox = new VBox(8, deviceTitle, deviceRow, copyHint, deviceHint);
        deviceBox.getStyleClass().add("card-inset");
        deviceBox.setStyle("-fx-padding: 12; -fx-background-radius: 10; -fx-border-radius: 10; -fx-border-width: 1;");

        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.getStyleClass().add("auth-error");
        feedback.setManaged(false);
        feedback.setVisible(false);

        Button importButton = Buttons.primary("Importer un fichier de licence...", "upload");
        importButton.setMaxWidth(Double.MAX_VALUE);
        Button folderButton = Buttons.secondary("Dossier de licence", "folder");
        Button quitButton = Buttons.ghost("Quitter", "x");

        HBox secondaryActions = new HBox(10, folderButton, quitButton);
        secondaryActions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(14, title, subtitle, statusBox, deviceBox, importButton,
                secondaryActions, feedback);
        card.getStyleClass().add("auth-card");
        card.setMaxWidth(560);

        // ------------------------------------------------------------ actions
        Runnable chooseFile = () -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Sélectionner le fichier de licence");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Licence TechShop (*.lic)", "*.lic"),
                    new FileChooser.ExtensionFilter("Tous les fichiers", "*.*"));
            Window window = card.getScene() == null ? null : card.getScene().getWindow();
            File selected = chooser.showOpenDialog(window);
            if (selected != null) {
                importLicense(selected.toPath(), importButton, feedback, onActivated);
            }
        };

        importButton.setOnAction(e -> chooseFile.run());
        folderButton.setOnAction(e -> openLicenseFolder(feedback));
        quitButton.setOnAction(e -> Platform.exit());

        card.setOnDragOver(event -> {
            Dragboard board = event.getDragboard();
            if (board.hasFiles() && board.getFiles().stream().anyMatch(LicenseScreen::isLicenseFile)) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        card.setOnDragDropped(event -> {
            Dragboard board = event.getDragboard();
            if (board.hasFiles()) {
                board.getFiles().stream()
                        .filter(LicenseScreen::isLicenseFile)
                        .findFirst()
                        .ifPresent(file -> importLicense(file.toPath(), importButton, feedback, onActivated));
                event.setDropCompleted(true);
            } else {
                event.setDropCompleted(false);
            }
            event.consume();
        });

        // ------------------------------------------------------------- layout
        VBox formSide = new VBox(card);
        formSide.getStyleClass().add("auth-form-side");

        HBox split = new HBox(brand, formSide);
        split.getStyleClass().add("auth-split");
        HBox.setHgrow(brand, Priority.ALWAYS);

        StackPane root = new StackPane(split);
        root.getStyleClass().add("app-root");
        root.getStyleClass().add("license-gate");
        return root;
    }

    private static void importLicense(Path file,
                                      Button importButton,
                                      Label feedback,
                                      Runnable onActivated) {
        try {
            LicenseService.LicenseStatus installed = LicenseService.install(file);
            showFeedback(feedback, "Licence installée pour " + installed.customerName()
                    + ". Ouverture de TechShop Manager...", false);
            importButton.setDisable(true);
            PauseTransition pause = new PauseTransition(Duration.millis(900));
            pause.setOnFinished(e -> onActivated.run());
            pause.play();
        } catch (LicenseService.LicenseInstallException ex) {
            showFeedback(feedback, ex.getMessage(), true);
        } catch (RuntimeException ex) {
            showFeedback(feedback, "La licence n'a pas pu être installée : " + ex.getMessage(), true);
        }
    }

    private static void showFeedback(Label feedback, String message, boolean error) {
        feedback.setText(message);
        feedback.getStyleClass().removeAll("auth-error", "auth-success");
        feedback.getStyleClass().add(error ? "auth-error" : "auth-success");
        feedback.setManaged(true);
        feedback.setVisible(true);
    }

    private static void openLicenseFolder(Label feedback) {
        try {
            Path directory = LicenseService.licenseDirectory();
            Files.createDirectories(directory);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(directory.toFile());
            } else {
                showFeedback(feedback, "Dossier de licence : " + directory, false);
            }
        } catch (Exception ex) {
            showFeedback(feedback, "Impossible d'ouvrir le dossier : " + ex.getMessage(), true);
        }
    }

    private static boolean isLicenseFile(File file) {
        return file != null && file.isFile()
                && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".lic");
    }
}
