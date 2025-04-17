package org.elitost.maven.plugins.checkers;

import org.elitost.maven.plugins.renderers.ReportRenderer;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Vérifie la présence de propriétés spécifiques dans un projet Maven.
 * Génère un rapport indiquant si certaines propriétés sont présentes ou manquantes.
 */
public class PropertyChecker {

    private final Log log;
    private final ReportRenderer renderer;

    public PropertyChecker(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    /**
     * Génère un rapport sur la présence des propriétés spécifiées dans le fichier POM du projet.
     *
     * @param project           Le projet Maven à analyser.
     * @param propertiesToCheck Liste des clés de propriétés à vérifier.
     * @return Un rapport formaté selon le renderer (Markdown, HTML, etc.).
     */
    public String generatePropertiesCheckReport(MavenProject project, List<String> propertiesToCheck) {
        StringBuilder report = new StringBuilder();
        report.append(renderer.renderHeader3("🔧 Vérification des Propriétés dans `" + project.getArtifactId() + "`"));

        try {
            Properties props = project.getProperties();
            List<String[]> rows = new ArrayList<>();

            for (String key : propertiesToCheck) {
                rows.add(createPropertyRow(key, props.containsKey(key)));
            }

            report.append(renderer.renderTable(new String[]{"Clé", "Statut"}, rows.toArray(new String[0][0])));

        } catch (Exception e) {
            log.error("[PropertyChecker] Exception levée", e);
            return renderErrorReport(e);
        }

        return report.toString();
    }

    /**
     * Crée une ligne pour le tableau du rapport indiquant le statut d'une propriété.
     *
     * @param key          La clé de la propriété à vérifier.
     * @param isPresent    Indique si la propriété est présente ou non.
     * @return Un tableau contenant la clé et le statut de la propriété.
     */
    private String[] createPropertyRow(String key, boolean isPresent) {
        String status = isPresent ? renderer.renderInfo("✅ Présente") : renderer.renderError("Manquante");
        logPropertyStatus(key, isPresent);
        return new String[]{key, status};
    }

    /**
     * Logue l'état de la propriété (présente ou manquante).
     *
     * @param key       La clé de la propriété.
     * @param isPresent Si la propriété est présente ou non.
     */
    private void logPropertyStatus(String key, boolean isPresent) {
        if (isPresent) {
            log.info("✅ [PropertyChecker] Propriété présente : " + key);
        } else {
            log.warn("❌ [PropertyChecker] Propriété manquante : " + key);
        }
    }

    /**
     * Génère un rapport d'erreur dans le cas où une exception survient.
     *
     * @param e L'exception levée pendant l'exécution.
     * @return Un rapport d'erreur formaté.
     */
    private String renderErrorReport(Exception e) {
        String errorMessage = "❌ Une erreur est survenue : " + e.getMessage();
        return renderer.renderError(errorMessage);
    }
}