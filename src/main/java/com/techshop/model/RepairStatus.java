package com.techshop.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cycle de vie d'un dossier de réparation (colonne repair_tickets.status,
 * SPEC §4.9). Chaque changement de statut est journalisé (repair_status_history)
 * et suit une transition valide : on ne saute jamais une étape, sauf annulation.
 *
 * Reçu -> Diagnostic -> En attente de pièce -> En réparation -> Prête -> Livrée
 * et, à tout moment avant la livraison, Annulée.
 */
public final class RepairStatus {

    public static final String RECU = "RECU";
    public static final String DIAGNOSTIC = "DIAGNOSTIC";
    public static final String ATTENTE_PIECE = "ATTENTE_PIECE";
    public static final String EN_REPARATION = "EN_REPARATION";
    public static final String PRETE = "PRETE";
    public static final String LIVREE = "LIVREE";
    public static final String ANNULEE = "ANNULEE";

    /** Colonnes du tableau Kanban, dans l'ordre d'affichage. */
    public static final List<String> BOARD = List.of(
            RECU, DIAGNOSTIC, ATTENTE_PIECE, EN_REPARATION, PRETE, LIVREE, ANNULEE);

    private static final java.util.Map<String, String> LABELS = java.util.Map.of(
            RECU, "Reçu",
            DIAGNOSTIC, "Diagnostic",
            ATTENTE_PIECE, "En attente de pièce",
            EN_REPARATION, "En réparation",
            PRETE, "Prête",
            LIVREE, "Livrée",
            ANNULEE, "Annulée");

    /** Transitions autorisées : état courant -> états cibles possibles. */
    private static final java.util.Map<String, java.util.Set<String>> TRANSITIONS = java.util.Map.of(
            RECU, java.util.Set.of(DIAGNOSTIC, ANNULEE),
            DIAGNOSTIC, java.util.Set.of(ATTENTE_PIECE, EN_REPARATION, ANNULEE),
            ATTENTE_PIECE, java.util.Set.of(DIAGNOSTIC, EN_REPARATION, ANNULEE),
            EN_REPARATION, java.util.Set.of(ATTENTE_PIECE, PRETE, ANNULEE),
            PRETE, java.util.Set.of(EN_REPARATION, LIVREE, ANNULEE),
            LIVREE, java.util.Set.of(),
            ANNULEE, java.util.Set.of());

    private RepairStatus() {
    }

    public static String label(String code) {
        return code == null ? "—" : LABELS.getOrDefault(code, code);
    }

    public static java.util.Set<String> allowedFrom(String from) {
        return TRANSITIONS.getOrDefault(from, java.util.Set.of());
    }

    /** La transition from -> to est-elle autorisée par le flux métier ? */
    public static boolean canTransition(String from, String to) {
        if (from == null || to == null || from.equals(to)) {
            return false;
        }
        return allowedFrom(from).contains(to);
    }

    /** Statut terminal : plus aucune transition (livrée ou annulée). */
    public static boolean isTerminal(String code) {
        return LIVREE.equals(code) || ANNULEE.equals(code);
    }

    /** Un dossier actif peut encore être en retard (ni livré ni annulé). */
    public static boolean isActive(String code) {
        return !isTerminal(code);
    }

    public static int order(String code) {
        int i = BOARD.indexOf(code);
        return i < 0 ? BOARD.size() : i;
    }

    /** Clé sémantique de couleur pour le badge (mappée côté interface). */
    public static String color(String code) {
        return switch (code == null ? "" : code) {
            case RECU -> "info";
            case DIAGNOSTIC -> "info";
            case ATTENTE_PIECE -> "warning";
            case EN_REPARATION -> "primary";
            case PRETE -> "success";
            case LIVREE -> "neutral";
            case ANNULEE -> "danger";
            default -> "neutral";
        };
    }
}
