package org.elitost.maven.plugins;

import org.apache.maven.project.MavenProject;

import java.util.List;
import java.util.Objects;

/**
 * Contexte d'exécution partagé entre les différents checkers.
 * Permet aux checkers d'accéder à des informations globales (projet racine, propriétés à vérifier, etc.).
 */
public class CheckerContext {

    private final MavenProject currentModule;
    private final MavenProject rootProject;
    private final List<String> propertiesToCheck;

    public CheckerContext(MavenProject currentModule, MavenProject rootProject, List<String> propertiesToCheck) {
        this.currentModule = Objects.requireNonNull(currentModule);
        this.rootProject = Objects.requireNonNull(rootProject);
        this.propertiesToCheck = propertiesToCheck != null ? propertiesToCheck : List.of();
    }

    public MavenProject getCurrentModule() {
        return currentModule;
    }

    public MavenProject getRootProject() {
        return rootProject;
    }

    public List<String> getPropertiesToCheck() {
        return propertiesToCheck;
    }
}