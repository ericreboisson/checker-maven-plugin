package org.elitost.maven.plugins.checkers;

import org.apache.maven.plugin.logging.Log;
import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Vérifie la présence de propriétés spécifiques dans un projet Maven.
 * Génère un rapport listant uniquement les propriétés manquantes.
 */
public class PropertyPresenceChecker implements CustomChecker, InitializableChecker {

    private Log log;
    private ReportRenderer renderer;

    /** Constructeur par défaut pour SPI */
    public PropertyPresenceChecker() {}

    @Override
    public void init(Log log,
                     org.eclipse.aether.RepositorySystem repoSystem,
                     org.eclipse.aether.RepositorySystemSession session,
                     List<org.eclipse.aether.repository.RemoteRepository> remoteRepositories,
                     ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    @Override
    public String getId() {
        return "propertyPresence";
    }

    /**
     * Génère un rapport listant uniquement les propriétés manquantes dans le POM.
     *
     * @param checkerContext Le contexte Maven (module courant, parent, etc.).
     * @return Rapport formaté selon le renderer.
     */
    @Override
    public String generateReport(CheckerContext checkerContext) {
        StringBuilder report = new StringBuilder();
        String artifactId = checkerContext.getCurrentModule().getArtifactId();

        report.append(renderer.renderHeader3("🔧 Propriétés manquantes dans `" + artifactId + "`"));
        report.append(renderer.openIndentedSection());

        List<String[]> missing;
        try {
            Properties props = checkerContext.getCurrentModule().getProperties();
            missing = new ArrayList<>();

            for (String key : checkerContext.getPropertiesToCheck()) {
                if (!props.containsKey(key)) {
                    log.warn("❌ [PropertyChecker] Propriété manquante : " + key);
                    missing.add(new String[]{key, renderer.renderParagraph("Manquante")});
                }
            }

            // Si des propriétés sont manquantes, on les liste
            if (!missing.isEmpty()) {
                report.append(renderer.renderTable(new String[]{"Clé", "Statut"}, missing.toArray(new String[0][0])));
            }

        } catch (Exception e) {
            log.error("[PropertyChecker] Exception levée", e);
            return renderErrorReport(e);
        }

        report.append(renderer.closeIndentedSection());

        // Retourne une chaîne vide si tout va bien (aucune propriété manquante)
        return missing.isEmpty() ? "" : report.toString();
    }

    /**
     * Génère un rapport d'erreur en cas d'exception.
     */
    private String renderErrorReport(Exception e) {
        return renderer.renderError("❌ Une erreur est survenue : " + e.getMessage());
    }
}