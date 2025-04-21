package org.elitost.maven.plugins.checkers;

import org.elitost.maven.plugins.CheckerContext;

/**
 * Interface commune pour tous les checkers Maven.
 * Permet de centraliser la logique SPI et d’unifier les appels.
 */
public interface CustomChecker {

    /**
     * Identifiant unique du checker, utilisé dans les paramètres `checkersToRun`.
     */
    String getId();

    /**
     * Génère un rapport pour le module courant.
     *
     * @param context Contexte global (module courant, projet parent, propriétés, etc.)
     * @return Rapport rendu (HTML, Markdown, etc.)
     */
    String generateReport(CheckerContext context);
}