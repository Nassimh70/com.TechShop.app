package com.techshop.model;

/** Codes d'action du journal d'activité (colonne activity_log.action). */
public final class ActivityActions {

    public static final String CONNEXION = "CONNEXION";
    public static final String ECHEC_CONNEXION = "ECHEC_CONNEXION";
    public static final String DECONNEXION = "DECONNEXION";
    public static final String CHANGEMENT_MDP = "CHANGEMENT_MDP";
    public static final String REINIT_MDP = "REINIT_MDP";
    public static final String CREATION = "CREATION";
    public static final String MODIFICATION = "MODIFICATION";
    public static final String ACTIVATION = "ACTIVATION";
    public static final String DESACTIVATION = "DESACTIVATION";
    public static final String PERMISSIONS = "PERMISSIONS";
    /** Mouvement de stock (entrée, sortie, perte, retour...). */
    public static final String STOCK_MOUVEMENT = "STOCK_MOUVEMENT";
    /** Ajustement manuel de stock avec motif. */
    public static final String STOCK_AJUSTEMENT = "STOCK_AJUSTEMENT";
    /** Import CSV de produits. */
    public static final String IMPORT_CSV = "IMPORT_CSV";
    /** Changement de prix de vente. */
    public static final String CHANGEMENT_PRIX = "CHANGEMENT_PRIX";
    /** Génération / impression d'étiquettes code-barres. */
    public static final String ETIQUETTES = "ETIQUETTES";
    /** Vente enregistrée au point de vente. */
    public static final String VENTE = "VENTE";
    /** Retour / remboursement d'une vente. */
    public static final String REMBOURSEMENT = "REMBOURSEMENT";
    /** Ouverture d'une session de caisse. */
    public static final String CAISSE_OUVERTURE = "CAISSE_OUVERTURE";
    /** Clôture d'une session de caisse. */
    public static final String CAISSE_CLOTURE = "CAISSE_CLOTURE";
    /** Entrée ou sortie d'espèces en caisse. */
    public static final String CAISSE_MOUVEMENT = "CAISSE_MOUVEMENT";

    private ActivityActions() {
    }
}
