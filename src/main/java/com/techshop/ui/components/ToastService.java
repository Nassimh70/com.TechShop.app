package com.techshop.ui.components;

import com.techshop.ui.Icons;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Notifications « toast » en haut à droite (SPEC §2) : succès, erreur, info,
 * alerte. Apparition par glissement + fondu, disparition automatique.
 */
public final class ToastService {

    public enum ToastType {SUCCESS, WARNING, ERROR, INFO}

    private static final ToastService INSTANCE = new ToastService();

    private VBox container;

    private ToastService() {
    }

    public static ToastService get() {
        return INSTANCE;
    }

    /** Branché par MainController (conteneur en haut à droite de la fenêtre). */
    public void init(StackPane overlayHost) {
        // Le calque couvre toute la fenêtre : sans cela il intercepterait tous les
        // clics de l'écran sous-jacent. Seuls les enfants porteurs d'un fond
        // (toasts, voile des dialogues) restent cliquables.
        overlayHost.setPickOnBounds(false);
        overlayHost.setMouseTransparent(false);

        if (container != null && container.getParent() instanceof javafx.scene.layout.Pane parent) {
            parent.getChildren().remove(container);
        }
        container = new VBox();
        container.getStyleClass().add("toast-container");
        container.setAlignment(Pos.TOP_RIGHT);
        // Conteneur transparent au clic : la zone vide autour des toasts ne doit
        // pas bloquer les interactions de l'écran.
        container.setPickOnBounds(false);
        overlayHost.getChildren().add(container);
        StackPane.setAlignment(container, Pos.TOP_RIGHT);
    }

    public void success(String title, String message) {
        show(ToastType.SUCCESS, title, message);
    }

    public void error(String title, String message) {
        show(ToastType.ERROR, title, message);
    }

    public void info(String title, String message) {
        show(ToastType.INFO, title, message);
    }

    public void warning(String title, String message) {
        show(ToastType.WARNING, title, message);
    }

    public void show(ToastType type, String title, String message) {
        if (container == null) {
            return;
        }
        javafx.application.Platform.runLater(() -> {
            ToastView toast = new ToastView(type, title, message);
            toast.setOnClose(() -> remove(toast));
            container.getChildren().add(toast);

            // Glissement depuis la droite + fondu (animation simple)
            TranslateTransition slide = new TranslateTransition(Duration.millis(180), toast);
            slide.setFromX(60);
            slide.setToX(0);
            FadeTransition fade = new FadeTransition(Duration.millis(180), toast);
            fade.setFromValue(0);
            fade.setToValue(1);
            slide.play();
            fade.play();

            // Disparition automatique (erreur : plus long, l'utilisateur doit lire)
            PauseTransition pause = new PauseTransition(
                    Duration.seconds(type == ToastType.ERROR ? 6 : 4));
            pause.setOnFinished(e -> remove(toast));
            pause.play();
        });
    }

    private void remove(ToastView toast) {
        if (container == null || !container.getChildren().contains(toast)) {
            return;
        }
        FadeTransition fade = new FadeTransition(Duration.millis(150), toast);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> container.getChildren().remove(toast));
        fade.play();
    }

    /** Vue interne d'un toast. */
    public static final class ToastView extends HBox {

        private final Runnable[] onClose = new Runnable[1];

        ToastView(ToastType type, String title, String message) {
            String css = switch (type) {
                case SUCCESS -> "toast-success";
                case WARNING -> "toast-warning";
                case ERROR -> "toast-error";
                case INFO -> "toast-info";
            };
            String icon = switch (type) {
                case SUCCESS -> "check-circle";
                case WARNING -> "alert-triangle";
                case ERROR -> "x-circle";
                case INFO -> "info";
            };
            getStyleClass().addAll("toast", css);
            setAlignment(Pos.CENTER_LEFT);

            var iconNode = Icons.feather(icon, 18);

            Label t = new Label(title);
            t.getStyleClass().add("toast-title");
            Label m = new Label(message);
            m.getStyleClass().add("toast-msg");
            m.setWrapText(true);
            VBox texts = new VBox(2, t, m);
            HBox.setHgrow(texts, Priority.ALWAYS);

            Button close = Buttons.ghostIcon("x", "Fermer");
            close.getStyleClass().add("toast-close");
            close.setGraphic(Icons.feather("x", 12));
            close.setOnAction(e -> {
                if (onClose[0] != null) {
                    onClose[0].run();
                }
            });

            getChildren().addAll(iconNode, texts, close);
        }

        void setOnClose(Runnable r) {
            onClose[0] = r;
        }
    }
}
