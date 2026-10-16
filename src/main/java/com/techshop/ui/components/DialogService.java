package com.techshop.ui.components;

import com.techshop.ui.Icons;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.concurrent.CompletableFuture;

/**
 * Dialogues modaux (SPEC §2) : voile assombri au-dessus de la fenêtre,
 * carte centrée rayon 16 avec ombre plus forte. Confirmation, formulaire,
 * et empreinte générique pour la palette de commandes.
 * Les toasts restent au-dessus du voile (dans le même overlay).
 */
public final class DialogService {

    private static final DialogService INSTANCE = new DialogService();

    private StackPane overlayHost;

    private DialogService() {
    }

    public static DialogService get() {
        return INSTANCE;
    }

    /** Branché par MainController. */
    public void init(StackPane host) {
        this.overlayHost = host;
        // Le calque couvre toute la fenêtre : sans cela il intercepterait tous les
        // clics de l'écran sous-jacent (saisie impossible). Le voile des dialogues
        // porte un fond opaque : il reste cliquable et bloque l'arrière-plan.
        if (host != null) {
            host.setPickOnBounds(false);
            host.setMouseTransparent(false);
        }
    }

    // ------------------------------- Public API -------------------------------

    /** Boîte de confirmation (« Supprimer définitivement ? »...). */
    public CompletableFuture<Boolean> confirm(String title, String message,
                                              String confirmLabel, boolean danger) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        Label msg = new Label(message);
        msg.getStyleClass().add("modal-msg");
        msg.setWrapText(true);
        msg.setMaxWidth(420);

        Button cancel = Buttons.secondary("Annuler");
        Button confirm = danger ? Buttons.danger(confirmLabel) : Buttons.primary(confirmLabel);

        HBox footer = new HBox(8, cancel, confirm);
        footer.getStyleClass().add("modal-footer");

        VBox card = card(title, "alert-triangle", msg, footer);
        ModalHandle handle = modal(card, true);

        cancel.setOnAction(e -> {
            future.complete(false);
            handle.close();
        });
        confirm.setOnAction(e -> {
            future.complete(true);
            handle.close();
        });
        handle.onClose().thenRun(() -> future.complete(false));
        return future;
    }

    /** Dialogue de formulaire : contenu arbitrary + bouton d'enregistrement. */
    public CompletableFuture<Boolean> form(String title, Node body, String confirmLabel) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        Button cancel = Buttons.secondary("Annuler");
        Button confirm = Buttons.primary(confirmLabel);

        HBox footer = new HBox(8, cancel, confirm);
        footer.getStyleClass().add("modal-footer");

        VBox card = card(title, null, body, footer);
        ModalHandle handle = modal(card, true);

        cancel.setOnAction(e -> {
            future.complete(false);
            handle.close();
        });
        confirm.setOnAction(e -> {
            future.complete(true);
            handle.close();
        });
        handle.onClose().thenRun(() -> future.complete(false));
        return future;
    }

    /** Emprise sur un dialogue personnalisé (palette de commandes, aperçu...). */
    public ModalHandle custom(Node card, boolean dismissOnOutsideClick) {
        return modal(card, dismissOnOutsideClick);
    }

    // -------------------------------- Interne ---------------------------------

    /** Construit la carte : en-tête (icône + titre + croix), corps, pied. */
    public VBox card(String title, String iconFeather, Node body, Node footer) {
        Label titleNode = new Label(title);
        titleNode.getStyleClass().add("modal-title");

        HBox header = new HBox(10);
        header.getStyleClass().add("modal-header");
        if (iconFeather != null) {
            header.getChildren().add(Icons.feather(iconFeather, 18));
        }
        header.getChildren().add(titleNode);

        VBox bodyBox = new VBox(body);
        bodyBox.getStyleClass().add("modal-body");

        VBox card = new VBox();
        card.getStyleClass().add("modal-card");
        card.getChildren().addAll(header, bodyBox);
        if (footer != null) {
            card.getChildren().add(footer);
        }
        return card;
    }

    /** Ajoute la carte dans l'overlay avec voile + animations + Échap. */
    private ModalHandle modal(Node card, boolean dismissOnOutside) {
        CompletableFuture<Void> closed = new CompletableFuture<>();
        if (overlayHost == null) {
            throw new IllegalStateException("DialogService non initialisé");
        }
        // Dans un StackPane, un enfant est étiré pour occuper tout l'espace : sans
        // contrainte la carte modale remplirait toute la fenêtre. On la limite à sa
        // taille préférée (en respectant les cartes qui imposent déjà une taille max).
        if (card instanceof Region region) {
            if (region.getMaxWidth() == Region.USE_COMPUTED_SIZE) {
                region.setMaxWidth(Region.USE_PREF_SIZE);
            }
            if (region.getMaxHeight() == Region.USE_COMPUTED_SIZE) {
                region.setMaxHeight(Region.USE_PREF_SIZE);
            }
        }

        StackPane voile = new StackPane(card);
        voile.getStyleClass().add("modal-overlay");
        voile.setAlignment(Pos.CENTER);
        voile.setFocusTraversable(true);

        // Le voile est inséré AVANT le conteneur de toasts : les toasts restent visibles
        int toastsIndex = overlayHost.getChildren().size();
        overlayHost.getChildren().add(Math.max(0, toastsIndex - 1), voile);

        // Apparition : léger zoom + fondu (simple)
        card.setOpacity(0);
        ScaleTransition scale = new ScaleTransition(Duration.millis(140), card);
        scale.setFromX(0.96);
        scale.setFromY(0.96);
        scale.setToX(1);
        scale.setToY(1);
        FadeTransition fade = new FadeTransition(Duration.millis(140), card);
        fade.setFromValue(0);
        fade.setToValue(1);
        scale.play();
        fade.play();
        voile.requestFocus();

        Runnable closeAction = () -> {
            if (!closed.isDone()) {
                FadeTransition out = new FadeTransition(Duration.millis(100), voile);
                out.setFromValue(1);
                out.setToValue(0);
                out.setOnFinished(e -> overlayHost.getChildren().remove(voile));
                out.play();
                closed.complete(null);
            }
        };

        // Fermeture par clic sur le voile (si autorisée)
        voile.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            if (dismissOnOutside && e.getTarget() == voile) {
                closeAction.run();
            }
        });
        // Fermeture par Échap
        voile.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                closeAction.run();
                e.consume();
            }
        });

        return new ModalHandle(voile, closeAction, closed);
    }

    /** Emprise de dialogue : fermeture + promesse de fin. */
    public static final class ModalHandle {

        private final Node node;
        private final Runnable closer;
        private final CompletableFuture<Void> future;

        ModalHandle(Node node, Runnable closer, CompletableFuture<Void> future) {
            this.node = node;
            this.closer = closer;
            this.future = future;
        }

        public void close() {
            closer.run();
        }

        public CompletableFuture<Void> onClose() {
            return future;
        }

        public Node node() {
            return node;
        }
    }
}
