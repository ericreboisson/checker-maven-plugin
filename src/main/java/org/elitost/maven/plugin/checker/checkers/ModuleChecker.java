package org.elitost.maven.plugin.checker.checkers;

import org.elitost.maven.plugin.checker.renderers.ReportRenderer;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Vérifie que les modules attendus (par convention) sont bien présents sur le disque
 * et déclarés dans le {@code pom.xml} parent d’un projet Maven multi-modules.
 *
 * <p>Les modules attendus sont générés dynamiquement à partir de l’identifiant
 * du projet parent suivi d’un suffixe standard tel que {@code -api}, {@code -impl}, etc.</p>
 */
public class ModuleChecker {

    private final Log log;
    private final ReportRenderer renderer;

    /**
     * Constructeur principal.
     *
     * @param log      Logger Maven
     * @param renderer Renderer de rapport (Markdown, HTML, etc.)
     */
    public ModuleChecker(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    /**
     * Génère un rapport de conformité des modules attendus dans un projet parent.
     *
     * @param project le projet Maven parent
     * @return un rapport de vérification au format du renderer fourni
     */
    public String generateModuleCheckReport(MavenProject project) {
        String artifactId = project.getArtifactId();
        StringBuilder report = new StringBuilder();

        report.append(renderer.renderTitle("🧩 Vérification des modules du projet `" + artifactId + "`"));

        try {
            List<String> expectedModules = getExpectedModules(artifactId);
            List<String> missingModules = findMissingModules(project, expectedModules);

            if (missingModules.isEmpty()) {
                String successMessage = "✅ Tous les modules attendus sont présents et correctement déclarés.";
                report.append(renderer.renderParagraph(successMessage));
                log.info("[ModuleChecker] " + successMessage);
            } else {
                Collections.sort(missingModules);
                report.append(renderer.renderParagraph("❌ Certains modules attendus sont absents ou non déclarés :"));

                String[][] rows = missingModules.stream()
                        .map(missing -> {
                            log.warn("[ModuleChecker] Module manquant : " + missing);
                            return new String[]{ "❌ " + missing };
                        })
                        .toArray(String[][]::new);

                report.append(renderer.renderTable(new String[]{"📦 Module manquant"}, rows));
                report.append(renderer.renderWarning(
                        "Vérifie que chaque module est présent sur le disque **et** déclaré dans la section `<modules>` du `pom.xml` parent."
                ));
            }

        } catch (Exception e) {
            String errorMessage = "Une erreur est survenue lors de la vérification des modules : `" + e.getMessage() + "`";
            report.append(renderer.renderError(errorMessage));
            log.error("[ModuleChecker] " + errorMessage, e);
        }

        return report.toString();
    }

    /**
     * Définit la convention de nommage des modules attendus : {@code artifactId-suffix}.
     *
     * @param baseArtifactId le nom de base du projet parent
     * @return liste complète des modules attendus
     */
    private List<String> getExpectedModules(String baseArtifactId) {
        return List.of(
                baseArtifactId + "-api",
                baseArtifactId + "-impl",
                baseArtifactId + "-local"
        );
    }

    /**
     * Vérifie quels modules sont absents physiquement ou non déclarés dans le pom parent.
     *
     * @param project         le projet Maven parent
     * @param expectedModules liste des modules attendus
     * @return liste des modules manquants ou non déclarés
     */
    private List<String> findMissingModules(MavenProject project, List<String> expectedModules) {
        List<String> missing = new ArrayList<>();

        for (String module : expectedModules) {
            boolean isDeclared = project.getModules().contains(module);
            boolean existsOnDisk = new File(project.getBasedir(), module).exists();

            if (!isDeclared || !existsOnDisk) {
                missing.add(module);
            }
        }

        return missing;
    }
}