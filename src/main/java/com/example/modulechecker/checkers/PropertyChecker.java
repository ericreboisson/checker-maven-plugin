package com.example.modulechecker.checkers;

import com.example.modulechecker.renderers.ReportRenderer;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class PropertyChecker {

    private final Log log;
    private final ReportRenderer renderer;

    public PropertyChecker(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    /**
     * Génère un rapport sur la présence de certaines propriétés dans le POM.
     * @param project Le projet Maven à analyser.
     * @param propertiesToCheck Liste des clés de propriétés à vérifier.
     * @return Rapport formaté (Markdown, HTML...) selon le renderer injecté.
     */
    public String generatePropertiesCheckReport(MavenProject project, List<String> propertiesToCheck) {
        StringBuilder report = new StringBuilder();
        report.append(renderer.renderTitle("🔧 Vérification des Propriétés dans `" + project.getArtifactId() + "`"));

        try {
            Properties props = project.getProperties();
            List<String[]> rows = new ArrayList<>();

            for (String key : propertiesToCheck) {
                if (props.containsKey(key)) {
                    rows.add(new String[]{key, renderer.renderInfo("✅ Présente")});
                    log.info("✅ [PropertyChecker] Propriété présente : " + key + " dans " + project.getArtifactId());
                } else {
                    rows.add(new String[]{key, renderer.renderError("Manquante")});
                    log.warn("❌ [PropertyChecker] Propriété manquante : " + key + " dans " + project.getArtifactId());
                }
            }

            report.append(renderer.renderTable(new String[]{"Clé", "Statut"}, rows.toArray(new String[0][0])));

        } catch (Exception e) {
            report.append(renderer.renderError("❌ Une erreur est survenue : " + e.getMessage()));
            log.error("[PropertyChecker] Exception levée", e);
        }

        return report.toString();
    }
}