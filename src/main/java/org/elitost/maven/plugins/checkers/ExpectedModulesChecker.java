package org.elitost.maven.plugins.checkers;

import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;
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
public class ExpectedModulesChecker implements CustomChecker, BasicInitializableChecker {

    private Log log;
    private ReportRenderer renderer;

    /** Constructeur requis pour le chargement SPI */
    public ExpectedModulesChecker() {
    }

    @Override
    public void init(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    @Override
    public String getId() {
        return "expectedModules";
    }

    @Override
    public String generateReport(CheckerContext checkerContext) {
        String artifactId = checkerContext.getCurrentModule().getArtifactId();
        StringBuilder report = new StringBuilder();

        report.append(renderer.renderHeader3("🧩 Vérification des modules du projet `" + artifactId + "`"));
        report.append(renderer.openIndentedSection());

        try {
            List<String> expectedModules = getExpectedModules(artifactId);
            List<String> missingModules = findMissingModules(checkerContext.getCurrentModule(), expectedModules);

            if (missingModules.isEmpty()) {
                String successMessage = "✅ Tous les modules attendus sont présents et correctement déclarés.";
                report.append(renderer.renderParagraph(successMessage));
                log.info("[ModuleChecker] " + successMessage);
            } else {
                Collections.sort(missingModules);
                report.append(renderer.renderError("Certains modules attendus sont absents ou non déclarés :"));

                String[][] rows = missingModules.stream()
                        .map(missing -> {
                            log.warn("[ModuleChecker] Module manquant : " + missing);
                            return new String[]{ missing };
                        })
                        .toArray(String[][]::new);

                report.append(renderer.renderTable(new String[]{"📦 Module manquant"}, rows));
                report.append(renderer.renderParagraph(
                        "💡 Vérifie que chaque module est présent sur le disque et déclaré dans la section `<modules>` du `pom.xml` parent."
                ));
            }

        } catch (Exception e) {
            String errorMessage = "Une erreur est survenue lors de la vérification des modules : `" + e.getMessage() + "`";
            report.append(renderer.renderError(errorMessage));
            log.error("[ModuleChecker] " + errorMessage, e);
        }

        report.append(renderer.closeIndentedSection());
        return report.toString();
    }

    private List<String> getExpectedModules(String baseArtifactId) {
        return List.of(
                baseArtifactId + "-api",
                baseArtifactId + "-impl",
                baseArtifactId + "-local"
        );
    }

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