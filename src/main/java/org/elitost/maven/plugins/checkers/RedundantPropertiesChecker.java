package org.elitost.maven.plugins.checkers;

import org.elitost.maven.plugins.renderers.ReportRenderer;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * Analyseur de propriétés redondantes dans un fichier pom.xml :
 * Détecte les propriétés définies dans un module mais jamais utilisées dans aucun pom du projet.
 */
public class RedundantPropertiesChecker {

    private final Log log;
    private final ReportRenderer renderer;

    public RedundantPropertiesChecker(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    /**
     * Génère un rapport listant les propriétés définies dans ce module mais non utilisées dans le projet.
     *
     * @param project Le projet Maven (racine ou module) à analyser.
     * @return Rapport formaté (Markdown, HTML...) ou chaîne vide si tout est utilisé.
     */
    public String generateRedundantPropertiesReport(MavenProject project) {
        String artifactId = project.getArtifactId();
        Properties definedProperties = Optional.ofNullable(project.getOriginalModel())
                .map(Model::getProperties)
                .orElse(new Properties());

        if (definedProperties.isEmpty()) {
            log.debug("[RedundantPropertiesChecker] Aucune propriété définie dans " + artifactId);
            return "";
        }

        List<File> pomFiles = collectAllPomFiles(project);
        String combinedPomContent = readAllPomContents(pomFiles);

        List<String[]> unusedProperties = new ArrayList<>();

        for (String property : definedProperties.stringPropertyNames()) {
            String propertyReference = "${" + property.trim() + "}";
            if (!combinedPomContent.contains(propertyReference)) {
                log.warn("⚠️ [RedundantPropertiesChecker] Propriété non utilisée : " + property + " (définie dans " + artifactId + ")");
                unusedProperties.add(new String[]{property}); // ⬅️ ici, plus de backticks
            }
        }

        if (unusedProperties.isEmpty()) {
            log.info("[RedundantPropertiesChecker] Toutes les propriétés sont utilisées dans " + artifactId);
            return "";
        }

        return renderReport(artifactId, unusedProperties);
    }

    private List<File> collectAllPomFiles(MavenProject project) {
        List<File> pomFiles = new ArrayList<>();
        collectPomFilesRecursive(project, pomFiles);
        return pomFiles;
    }

    private void collectPomFilesRecursive(MavenProject project, List<File> pomFiles) {
        File pom = project.getFile();
        if (pom != null && pom.exists()) {
            pomFiles.add(pom);
        }

        List<MavenProject> children = project.getCollectedProjects();
        if (children != null) {
            for (MavenProject child : children) {
                collectPomFilesRecursive(child, pomFiles);
            }
        }
    }

    private String readAllPomContents(List<File> pomFiles) {
        StringBuilder content = new StringBuilder();

        for (File pom : pomFiles) {
            try {
                content.append(Files.readString(pom.toPath())).append('\n');
            } catch (Exception e) {
                log.warn("⚠️ [RedundantPropertiesChecker] Impossible de lire le fichier : " + pom.getAbsolutePath(), e);
            }
        }

        return content.toString();
    }

    private String renderReport(String artifactId, List<String[]> unusedProperties) {
        StringBuilder report = new StringBuilder();
        report.append(renderer.renderHeader3("🧹 Propriétés Redondantes dans `" + artifactId + "`"));
        report.append(renderer.renderParagraph(
                "Les propriétés suivantes sont définies dans ce module mais ne sont référencées dans aucun `pom.xml` du projet :"
        ));
        report.append(renderer.renderTable(new String[]{"Nom de la propriété"}, unusedProperties.toArray(new String[0][])));
        return report.toString();
    }
}