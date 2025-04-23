package org.elitost.maven.plugins.utils;

/**
 * Classe utilitaire pour les symboles communs utilisés dans les logs et rapports.
 */
public final class Symbols {

    public static final String ERROR = "❌ ";
    public static final String OK = "✅ ";
    public static final String WARNING = "⚠️ ";
    public static final String TIMEOUT = "⏱️ ";

    // Constructeur privé pour empêcher l'instanciation
    private Symbols() {
        throw new UnsupportedOperationException("Cette classe utilitaire ne peut pas être instanciée.");
    }
}