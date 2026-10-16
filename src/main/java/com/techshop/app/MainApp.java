package com.techshop.app;

import javafx.application.Application;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Point d'entrée de TechShop Manager (SPEC §1). La fenêtre et le flux de
 * démarrage (base -> connexion -> fenêtre principale) sont gérés par
 * {@link AppShell} : ce point d'entrée ne fait que lancer JavaFX.
 */
public class MainApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    @Override
    public void start(Stage stage) {
        log.info("Démarrage de TechShop Manager");
        AppShell.start(stage);
    }

    @Override
    public void stop() {
        AppShell.stop();
        log.info("Arrêt de TechShop Manager");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
