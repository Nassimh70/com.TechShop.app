package com.techshop.app;

/**
 * Point d'entrée utilisé par le paquetage (jpackage).
 *
 * <p>Cette classe n'étend volontairement pas {@link javafx.application.Application} :
 * le lanceur Java des applications empaquetées refuse une classe principale qui
 * hérite d'{@code Application} lorsque JavaFX se trouve sur le classpath plutôt
 * que sur le module path. Déléguer à {@link MainApp} évite cette restriction et
 * permet à l'application non modulaire de démarrer normalement.</p>
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        MainApp.main(args);
    }
}
