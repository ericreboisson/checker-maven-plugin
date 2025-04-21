package org.elitost.maven.plugins.checkers;

import org.apache.maven.plugin.logging.Log;
import org.elitost.maven.plugins.renderers.ReportRenderer;

/**
 * Interface d’initialisation de base pour les checkers ne nécessitant que log et renderer.
 */
public interface BasicInitializableChecker {
    void init(Log log, ReportRenderer renderer);
}