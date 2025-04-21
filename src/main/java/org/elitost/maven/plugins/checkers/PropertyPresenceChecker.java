package org.elitost.maven.plugins.checkers;

import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Vérifie la présence de propriétés spécifiques dans un projet Maven.
 * Génère un rapport listant uniquement les propriétés manquantes.
 */
public class PropertyPresenceChecker implements CustomChecker{

    private final Log log;
    private final ReportRenderer renderer;

    public PropertyPresenceChecker(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    @Override
    public String getId() {
        return "";
    }

    /**
     * Génère un rapport listant uniquement les propriétés manquantes dans le POM.
     *
     * @param checkerContext           Le projet Maven à analyser.
     * @return Rapport HTML/Markdown/texte selon le renderer.
     */
    @Override
    public String generateReport(CheckerContext checkerContext) {

        StringBuilder report = new StringBuilder();
        report.append(renderer.renderHeader3("🔧 Propriétés manquantes dans `" + checkerContext.getCurrentModule().getArtifactId() + "`"));
        report.append(renderer.openIndentedSection());

        try {
            Properties props = checkerContext.getCurrentModule().getProperties();
            List<String[]> missing = new ArrayList<>();

            for (String key : checkerContext.getPropertiesToCheck()) {
                if (!props.containsKey(key)) {
                    log.warn("❌ [PropertyChecker] Propriété manquante : " + key);
                    missing.add(new String[]{key, renderer.renderError("Manquante")});
                }
            }

            if (missing.isEmpty()) {
                report.append(renderer.renderParagraph("✅ Toutes les propriétés attendues sont définies."));
            } else {
                report.append(renderer.renderTable(new String[]{"Clé", "Statut"}, missing.toArray(new String[0][0])));
            }

        } catch (Exception e) {
            log.error("[PropertyChecker] Exception levée", e);
            return renderErrorReport(e);
        }

        report.append(renderer.closeIndentedSection());
        return report.toString();
    }

    /**
     * Génère un rapport d'erreur.
     */
    private String renderErrorReport(Exception e) {
        String errorMessage = "❌ Une erreur est survenue : " + e.getMessage();
        return renderer.renderError(errorMessage);
    }

}