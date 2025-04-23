package org.elitost.maven.plugins.checkers;

import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.model.Dependency;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Analyseur de dépendances potentiellement non utilisées dans le code source Java.
 */
public class UnusedDependenciesChecker implements CustomChecker, BasicInitializableChecker {

    private static final String CHECKER_ID = "unusedDependencies";
    private static final String LOG_PREFIX = "[UnusedDependenciesChecker]";
    private static final String JAVA_SOURCE_DIR = "src/main/java";
    private static final String JAVA_FILE_EXTENSION = ".java";

    private Log log;
    private ReportRenderer renderer;
    private final Map<String, List<String>> usageHints;

    public UnusedDependenciesChecker() {
        this.usageHints = initializeUsageHints();
    }

    @Override
    public void init(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
        log.debug(LOG_PREFIX + " Initialisé");
    }

    @Override
    public String getId() {
        return CHECKER_ID;
    }

    @Override
    public String generateReport(CheckerContext checkerContext) {
        try {
            MavenProject project = checkerContext.getCurrentModule();
            log.info(LOG_PREFIX + " Analyse des dépendances pour le module : " + project.getArtifactId());

            String javaSourceContent = getCombinedJavaSourceContent(project);
            List<Dependency> unusedDependencies = findUnusedDependencies(project, javaSourceContent);

            return buildReport(project, unusedDependencies);
        } catch (Exception e) {
            log.error(LOG_PREFIX + " Erreur lors de l'analyse", e);
            return renderer.renderError("❌ Erreur lors de l'analyse des dépendances: " + e.getMessage());
        }
    }

    private String getCombinedJavaSourceContent(MavenProject project) {
        Path sourceDir = new File(project.getBasedir(), JAVA_SOURCE_DIR).toPath();
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(JAVA_FILE_EXTENSION))
                    .parallel()
                    .map(this::readFileContent)
                    .collect(Collectors.joining("\n"))
                    .toLowerCase();
        } catch (IOException e) {
            log.warn(LOG_PREFIX + " Impossible de lire les fichiers sources", e);
            return "";
        }
    }

    private List<Dependency> findUnusedDependencies(MavenProject project, String javaSourceContent) {
        return Optional.ofNullable(project.getOriginalModel())
                .map(model -> model.getDependencies().stream()
                        .filter(dep -> !"test".equalsIgnoreCase(dep.getScope()))
                        .filter(dep -> !isDependencyUsed(dep, javaSourceContent))
                        .peek(dep -> log.warn(String.format(
                                "⚠️ %s Dépendance non utilisée détectée : %s:%s",
                                LOG_PREFIX,
                                dep.getGroupId(),
                                dep.getArtifactId()
                        )))
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    private boolean isDependencyUsed(Dependency dep, String javaSourceContent) {
        String dependencyKey = dep.getGroupId() + ":" + dep.getArtifactId();
        return usageHints.getOrDefault(dependencyKey, List.of(
                        dep.getGroupId(),
                        dep.getArtifactId()
                )).stream()
                .map(String::toLowerCase)
                .anyMatch(javaSourceContent::contains);
    }

    private String buildReport(MavenProject project, List<Dependency> unusedDeps) {
        if (unusedDeps.isEmpty()) {
            return "";
        }

        StringBuilder report = new StringBuilder();
        report.append(renderer.renderHeader3("🔍 Dépendances non utilisées dans `" + project.getArtifactId() + "`"));
        report.append(renderer.openIndentedSection());
        report.append(renderer.renderWarning("Dépendances potentiellement inutilisées détectées :"));

        String[][] tableData = unusedDeps.stream()
                .map(dep -> new String[]{
                        dep.getGroupId(),
                        dep.getArtifactId(),
                        Optional.ofNullable(dep.getVersion()).orElse("inconnue")
                })
                .toArray(String[][]::new);

        report.append(renderer.renderTable(
                new String[]{"GroupId", "ArtifactId", "Version"},
                tableData
        ));
        report.append(renderer.closeIndentedSection());

        return report.toString();
    }

    private String readFileContent(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            log.warn(LOG_PREFIX + " Erreur de lecture du fichier: " + file, e);
            return "";
        }
    }

    private Map<String, List<String>> initializeUsageHints() {
        Map<String, List<String>> hints = new ConcurrentHashMap<>();
        hints.put("commons-io:commons-io", List.of("org.apache.commons.io", "fileutils"));
        hints.put("org.apache.commons:commons-lang3", List.of("org.apache.commons.lang3", "stringutils"));
        hints.put("com.google.guava:guava", List.of("com.google.common", "lists", "immutablelist"));
        hints.put("org.slf4j:slf4j-api", List.of("org.slf4j", "logger", "loggerfactory"));
        hints.put("org.junit.jupiter:junit-jupiter", List.of("org.junit.jupiter", "test", "jupiter"));
        hints.put("org.mockito:mockito-core", List.of("org.mockito", "mock", "mockito"));
        return hints;
    }
}