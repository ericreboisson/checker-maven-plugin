package org.elitost.maven.plugins.checkers;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyseur de propriétés redondantes dans un fichier pom.xml.
 * Détecte les propriétés définies dans un module, mais jamais utilisées dans aucun pom du projet.
 */
public class RedundantPropertiesChecker implements CustomChecker, BasicInitializableChecker {

    private static final String CHECKER_ID = "redundantProperties";
    private static final String LOG_PREFIX = "[RedundantPropertiesChecker]";
    private static final String PROPERTY_REF_FORMAT = "${%s}";

    private Log log;
    private ReportRenderer renderer;

    public RedundantPropertiesChecker() {
        // Constructeur sans argument requis pour SPI
    }

    @Override
    public void init(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    @Override
    public String getId() {
        return CHECKER_ID;
    }

    @Override
    public String generateReport(CheckerContext checkerContext) {
        try {
            MavenProject currentModule = checkerContext.getCurrentModule();
            String artifactId = currentModule.getArtifactId();

            Set<String> definedProperties = getDefinedProperties(currentModule);
            if (definedProperties.isEmpty()) {
                log.debug(LOG_PREFIX + " Aucune propriété définie dans " + artifactId);
                return "";
            }

            String combinedPomContent = getCombinedPomContent(currentModule);
            List<String> unusedProperties = findUnusedProperties(definedProperties, combinedPomContent, artifactId);

            return buildReport(artifactId, unusedProperties);
        } catch (Exception e) {
            log.error(LOG_PREFIX + " Exception levée", e);
            return renderer.renderError("❌ Erreur lors de l'analyse des propriétés redondantes: " + e.getMessage());
        }
    }

    private Set<String> getDefinedProperties(MavenProject project) {
        return Optional.ofNullable(project.getOriginalModel())
                .map(Model::getProperties)
                .map(Properties::stringPropertyNames)
                .orElse(Collections.emptySet());
    }

    private String getCombinedPomContent(MavenProject project) {
        return collectAllPomFiles(project).stream()
                .map(this::readPomContent)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
    }

    private List<File> collectAllPomFiles(MavenProject project) {
        List<File> pomFiles = new ArrayList<>();
        collectPomFilesRecursive(project, pomFiles);
        return pomFiles;
    }

    private void collectPomFilesRecursive(MavenProject project, List<File> pomFiles) {
        Optional.ofNullable(project.getFile())
                .filter(File::exists)
                .ifPresent(pomFiles::add);

        Optional.ofNullable(project.getCollectedProjects())
                .ifPresent(children -> children.forEach(child -> collectPomFilesRecursive(child, pomFiles)));
    }

    private String readPomContent(File pomFile) {
        try {
            return Files.readString(pomFile.toPath());
        } catch (Exception e) {
            log.warn(LOG_PREFIX + " Impossible de lire le fichier: " + pomFile.getAbsolutePath(), e);
            return null;
        }
    }

    private List<String> findUnusedProperties(Set<String> definedProperties, String pomContent, String artifactId) {
        return definedProperties.stream()
                .filter(property -> !isPropertyUsed(property, pomContent))
                .peek(property -> log.warn(String.format(
                        "⚠️ %s Propriété non utilisée: %s (définie dans %s)",
                        LOG_PREFIX, property, artifactId
                )))
                .collect(Collectors.toList());
    }

    private boolean isPropertyUsed(String property, String pomContent) {
        return pomContent.contains(String.format(PROPERTY_REF_FORMAT, property.trim()));
    }

    private String buildReport(String artifactId, List<String> unusedProperties) {
        if (unusedProperties.isEmpty()) {
            log.info(LOG_PREFIX + " Toutes les propriétés sont utilisées dans " + artifactId);
            return "";
        }

        StringBuilder report = new StringBuilder();
        report.append(renderer.renderHeader3("🧹 Propriétés Redondantes dans `" + artifactId + "`"));
        report.append(renderer.openIndentedSection());
        report.append(renderer.renderWarning(
                "Les propriétés suivantes sont définies dans ce module mais ne sont référencées dans aucun pom.xml du projet:"
        ));

        String[][] tableData = unusedProperties.stream()
                .map(property -> new String[]{property})
                .toArray(String[][]::new);

        report.append(renderer.renderTable(new String[]{"Nom de la propriété"}, tableData));
        report.append(renderer.closeIndentedSection());

        return report.toString();
    }
}