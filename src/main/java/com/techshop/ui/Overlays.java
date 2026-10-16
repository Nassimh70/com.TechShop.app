package com.techshop.ui;

import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.ToastService;
import javafx.scene.layout.StackPane;

/**
 * Branchement des services d'overlay (toasts + dialogues) sur un hôte.
 * Les écrans hors fenêtre principale (connexion, erreur de connexion...)
 * possèdent leur propre calque ; on l'active via cette fabrique.
 */
public final class Overlays {

    private Overlays() {
    }

    /** Branche toasts et dialogues sur le calque fourni. */
    public static void attach(StackPane host) {
        ToastService.get().init(host);
        DialogService.get().init(host);
    }
}
