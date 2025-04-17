package org.elitost.maven.plugin.checker.checkers;

import org.elitost.maven.plugin.checker.renderers.ReportRenderer;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyseur de dépendances potentiellement non utilisées dans le code source Java.
 */
public class UnusedDependenciesChecker {

    private final Log log;
    private final ReportRenderer renderer;

    public UnusedDependenciesChecker(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
        log.debug("[UnusedDependenciesChecker] Initialisé");
    }

    /**
     * Génère un rapport sur les dépendances probablement inutilisées dans le module.
     *
     * @param project Le projet Maven à analyser.
     * @return Rapport au format Markdown (ou HTML selon le renderer utilisé).
     */
    public String generateReport(MavenProject project) {
        log.info("[UnusedDependenciesChecker] Analyse des dépendances pour le module : " + project.getArtifactId());

        // 📂 Lecture de tous les fichiers Java
        List<File> javaFiles = collectJavaFiles(new File(project.getBasedir(), "src/main/java"));
        String fullJavaSource = javaFiles.stream()
                .map(this::readFileContent)
                .collect(Collectors.joining("\n"))
                .toLowerCase(); // Pour une comparaison insensible à la casse

        // 🔍 Détection des dépendances non utilisées
        List<Dependency> unusedDependencies = analyzeDependencyUsage(project, fullJavaSource);

        // 📝 Génération du rapport
        return renderReport(project, unusedDependencies);
    }

    /**
     * Analyse les dépendances du projet et identifie celles qui semblent inutilisées dans le code source.
     */
    private List<Dependency> analyzeDependencyUsage(MavenProject project, String javaCode) {
        Map<String, List<String>> usageHints = getDefaultUsageHints();

        List<Dependency> unused = new ArrayList<>();
        for (Dependency dep : project.getOriginalModel().getDependencies()) {
            if ("test".equalsIgnoreCase(dep.getScope())) continue;

            String key = dep.getGroupId() + ":" + dep.getArtifactId();
            List<String> hints = usageHints.getOrDefault(key, List.of(
                    dep.getGroupId(), dep.getArtifactId()
            ));

            boolean used = hints.stream()
                    .map(String::toLowerCase)
                    .anyMatch(javaCode::contains);

            if (!used) {
                log.warn("⚠️ [UnusedDependenciesChecker] Dépendance non utilisée détectée : " + key);
                unused.add(dep);
            }
        }
        return unused;
    }

    /**
     * Génère le contenu du rapport pour les dépendances inutilisées.
     */
    private String renderReport(MavenProject project, List<Dependency> unusedDeps) {
        StringBuilder report = new StringBuilder();
        report.append(renderer.renderTitle("🔍 Dépendances non utilisées dans `" + project.getArtifactId() + "`"));

        if (unusedDeps.isEmpty()) {
            report.append(renderer.renderInfo("✅ Toutes les dépendances semblent utilisées."));
        } else {
            report.append(renderer.renderWarning("⚠️ Dépendances potentiellement inutilisées détectées :"));

            String[][] rows = unusedDeps.stream()
                    .map(dep -> new String[]{
                            "`" + dep.getGroupId() + "`",
                            "`" + dep.getArtifactId() + "`",
                            dep.getVersion() != null ? "`" + dep.getVersion() + "`" : "_inconnue_"
                    })
                    .toArray(String[][]::new);

            report.append(renderer.renderTable(
                    new String[]{"GroupId", "ArtifactId", "Version"},
                    rows
            ));
        }

        return report.toString();
    }

    /**
     * Récupère récursivement tous les fichiers .java dans le dossier donné.
     */
    private List<File> collectJavaFiles(File dir) {
        if (!dir.exists()) return Collections.emptyList();

        List<File> files = new ArrayList<>();
        File[] entries = dir.listFiles();
        if (entries == null) return files;

        for (File file : entries) {
            if (file.isDirectory()) {
                files.addAll(collectJavaFiles(file));
            } else if (file.getName().endsWith(".java")) {
                files.add(file);
            }
        }
        return files;
    }

    /**
     * Lit le contenu d’un fichier texte, ou retourne une chaîne vide en cas d’erreur.
     */
    private String readFileContent(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException e) {
            log.warn("❌ Erreur de lecture du fichier : " + file.getAbsolutePath(), e);
            return "";
        }
    }

    /**
     * Table heuristique de correspondance des dépendances connues avec des indices de présence dans le code.
     */
    private Map<String, List<String>> getDefaultUsageHints() {
        Map<String, List<String>> hints = new HashMap<>();
        hints.put("commons-io:commons-io", List.of("org.apache.commons.io", "fileutils"));
        hints.put("org.apache.commons:commons-lang3", List.of("org.apache.commons.lang3", "stringutils"));
        hints.put("com.google.guava:guava", List.of("com.google.common", "lists", "immutablelist"));
        hints.put("org.slf4j:slf4j-api", List.of("org.slf4j", "logger", "loggerfactory"));
        return hints;
    }
}