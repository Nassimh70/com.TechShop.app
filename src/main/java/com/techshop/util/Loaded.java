package com.techshop.util;

import javafx.scene.Parent;

/** Vue chargée : racine + contrôleur (nullable si la vue n'en déclare pas). */
public record Loaded(Parent root, Object controller) {
}
