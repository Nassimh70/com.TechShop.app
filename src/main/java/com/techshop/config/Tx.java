package com.techshop.config;

import java.sql.SQLException;

/**
 * Exécution transactionnelle (SPEC §1 : « transactions pour toute opération
 * touchant plusieurs tables »). Les DAO ouvrant chacun une connexion, la
 * transaction en cours est portée par le thread (voir {@link DatabaseManager})
 * et partagée par tous les DAO appelés dans le bloc.
 * Les blocs imbriqués rejoignent la transaction du bloc englobant.
 */
public final class Tx {

    /** Bloc de travail pouvant lever une erreur SQL. */
    @FunctionalInterface
    public interface Work<T> {
        T run() throws SQLException;
    }

    private Tx() {
    }

    /**
     * Exécute un bloc dans une transaction : valide en cas de succès, annule en
     * cas d'erreur (SQL ou métier). Sans base active (tests), le bloc s'exécute
     * directement.
     */
    public static <T> T run(Work<T> work) throws SQLException {
        boolean proprietaire = !DatabaseManager.inTransaction();
        try {
            if (proprietaire) {
                DatabaseManager.beginTransaction();
            }
            T resultat = work.run();
            if (proprietaire) {
                DatabaseManager.commitTransaction();
            }
            return resultat;
        } catch (SQLException | RuntimeException e) {
            if (proprietaire) {
                DatabaseManager.rollbackTransaction();
            }
            throw e;
        } finally {
            if (proprietaire) {
                DatabaseManager.endTransaction();
            }
        }
    }
}
