package com.example.modulechecker.checkers;

import com.example.modulechecker.renderers.ReportRenderer;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class UnusedDependenciesChecker {

    private final Log log;
    private final ReportRenderer renderer;

    public UnusedDependenciesChecker(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
        log.debug("UnusedDependenciesChecker initialisé");
    }

    public String generateReport(MavenProject project) {
        StringBuilder report = new StringBuilder();
        report.append(renderer.renderTitle("🔍 Dépendances non utilisées"));

        List<File> javaFiles = collectJavaFiles(new File(project.getBasedir(), "src/main/java"));
        String javaCode = javaFiles.stream()
                .map(this::readFileContent)
                .collect(Collectors.joining("\n"));

        // table heuristique : groupId/artifactId => identifiants attendus dans le code
        Map<String, List<String>> usageHints = Map.of(
            "commons-io:commons-io", List.of("org.apache.commons.io", "FileUtils"),
            "org.apache.commons:commons-lang3", List.of("org.apache.commons.lang3", "StringUtils"),
            "com.google.guava:guava", List.of("com.google.common", "Lists", "ImmutableList"),
            "org.slf4j:slf4j-api", List.of("org.slf4j", "Logger", "LoggerFactory")
        );

        List<Dependency> unusedDeps = new ArrayList<>();

        for (Dependency dependency : project.getOriginalModel().getDependencies()) {
            if ("test".equalsIgnoreCase(dependency.getScope())) continue;

            String key = dependency.getGroupId() + ":" + dependency.getArtifactId();
            List<String> hints = usageHints.getOrDefault(key, List.of(
                dependency.getGroupId(), dependency.getArtifactId()
            ));

            boolean used = hints.stream().anyMatch(javaCode::contains);
            if (!used) {
                unusedDeps.add(dependency);
            }
        }

        if (unusedDeps.isEmpty()) {
            report.append(renderer.renderInfo("✅ Toutes les dépendances sont utilisées."));
        } else {
            report.append(renderer.renderWarning("⚠️ Dépendances potentiellement inutilisées trouvées :"));

            String[][] rows = unusedDeps.stream()
                    .map(dep -> new String[]{dep.getGroupId(), dep.getArtifactId(), dep.getVersion()})
                    .toArray(String[][]::new);

            report.append(renderer.renderTable(new String[]{"GroupId", "ArtifactId", "Version"}, rows));
        }

        return report.toString();
    }

    private List<File> collectJavaFiles(File dir) {
        if (!dir.exists()) return Collections.emptyList();

        File[] entries = dir.listFiles();
        if (entries == null) return Collections.emptyList();

        List<File> files = new ArrayList<>();
        for (File file : entries) {
            if (file.isDirectory()) {
                files.addAll(collectJavaFiles(file));
            } else if (file.getName().endsWith(".java")) {
                files.add(file);
            }
        }
        return files;
    }

    private String readFileContent(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException e) {
            log.warn("Erreur de lecture fichier : " + file.getAbsolutePath(), e);
            return "";
        }
    }
}