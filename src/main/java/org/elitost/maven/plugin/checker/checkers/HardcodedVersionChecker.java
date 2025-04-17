package org.elitost.maven.plugin.checker.checkers;

import org.elitost.maven.plugin.checker.renderers.ReportRenderer;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;

/**
 * Vérifie que les dépendances d’un projet Maven ne contiennent pas de versions codées en dur dans le `pom.xml`.
 * <p>
 * Ce checker a pour objectif d’encourager l’usage de propriétés Maven dans la section {@code <properties>},
 * facilitant ainsi la maintenance, la montée de versions centralisée et les bonnes pratiques de configuration.
 * </p>
 *
 * <p>Le rapport est généré au format choisi (Markdown, HTML…) via une implémentation de {@link ReportRenderer}.</p>
 *
 * @author Eric
 */
public class HardcodedVersionChecker {

    private static final String ANCHOR_ID = "hardcoded-versions";

    private final Log log;
    private final ReportRenderer renderer;

    /**
     * Construit un checker des versions codées en dur.
     *
     * @param log      le logger Maven pour afficher les informations et avertissements
     * @param renderer le renderer responsable de la génération du rapport (Markdown, HTML, etc.)
     */
    public HardcodedVersionChecker(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    /**
     * Génère un rapport listant les dépendances dont les versions sont codées en dur.
     *
     * @param project le projet Maven à analyser
     * @return une chaîne contenant le rapport formaté (Markdown, HTML, etc.)
     */
    public String generateHardcodedVersionReport(MavenProject project) {
        StringBuilder report = new StringBuilder();
        List<Dependency> hardcodedDeps = findHardcodedDependencies(project);

        if (hardcodedDeps.isEmpty()) {
            String message = String.format("✅ Aucune dépendance avec version codée en dur trouvée dans le module `%s`.",
                    project.getArtifactId());
            report.append(renderer.renderInfo(message));
            log.info("[HardcodedVersionChecker] " + message);
            return report.toString();
        }

        // Rapport
        report.append(renderer.renderAnchor(ANCHOR_ID));
        report.append(renderer.renderTitle("🧱 Versions codées en dur détectées"));
        report.append(renderer.renderParagraph(
                "Les dépendances suivantes utilisent une version définie en dur dans le `pom.xml`, au lieu d’une propriété `${...}`.\n" +
                        "Cela nuit à la centralisation et à la maintenabilité des versions."));

        // Table des dépendances concernées
        String[] headers = { "🏷️ Group ID", "📘 Artifact ID", "🔢 Version codée en dur" };
        String[][] rows = hardcodedDeps.stream()
                .map(dep -> {
                    log.warn(String.format("[HardcodedVersionChecker] Version codée en dur : %s:%s:%s",
                            dep.getGroupId(), dep.getArtifactId(), dep.getVersion()));
                    return new String[]{
                            dep.getGroupId(),
                            dep.getArtifactId(),
                            renderer.renderWarning(dep.getVersion())
                    };
                })
                .toArray(String[][]::new);

        report.append(renderer.renderTable(headers, rows));
        report.append(renderer.renderParagraph(
                "💡 *Conseil : remplace chaque version codée en dur par une propriété Maven définie dans la section `<properties>` du parent ou d’un BOM.*"));

        return report.toString();
    }

    /**
     * Recherche les dépendances définies avec une version codée en dur (non dynamique).
     *
     * @param project le projet Maven à inspecter
     * @return une liste de dépendances avec une version fixe (non ${...})
     */
    private List<Dependency> findHardcodedDependencies(MavenProject project) {
        List<Dependency> result = new ArrayList<>();

        List<Dependency> declaredDependencies = project.getOriginalModel().getDependencies();
        if (declaredDependencies == null || declaredDependencies.isEmpty()) {
            return result;
        }

        for (Dependency dependency : declaredDependencies) {
            String version = dependency.getVersion();
            if (version != null && !version.trim().isEmpty() && !version.trim().startsWith("${")) {
                result.add(dependency);
            }
        }

        return result;
    }
}