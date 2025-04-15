package com.example.modulechecker.checkers;

import com.example.modulechecker.renderers.ReportRenderer;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;

public class HardcodedVersionChecker {

    private final Log log;
    private final ReportRenderer renderer;

    public HardcodedVersionChecker(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    /**
     * Génère un rapport sur les dépendances avec version codée en dur.
     */
    public String generateHardcodedVersionReport(MavenProject project) {
        StringBuilder report = new StringBuilder();
        List<Dependency> hardcodedDeps = getHardcodedDependencies(project);

        if (hardcodedDeps.isEmpty()) {
            String message = "✅ Aucune dépendance avec version codée en dur trouvée dans le module `" + project.getArtifactId() + "`.";
            report.append(renderer.renderInfo(message));
            log.info("[HardcodedVersionChecker] " + message);
        } else {
            report.append(renderer.renderTitle("🧱 Versions codées en dur détectées"));
            report.append(renderer.renderParagraph("Les dépendances suivantes utilisent une version directement inscrite dans le `pom.xml`, sans propriété `${...}`.\nCela nuit à la centralisation des versions et complique la maintenance."));

            String[][] rows = hardcodedDeps.stream()
                    .map(dep -> {
                        log.warn("[HardcodedVersionChecker] Version codée en dur : " +
                                dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion());
                        return new String[]{
                                dep.getGroupId(),
                                dep.getArtifactId(),
                                renderer.renderWarning(dep.getVersion())
                        };
                    })
                    .toArray(String[][]::new);

            report.append(renderer.renderTable(
                    new String[]{"🏷️ Group ID", "📘 Artifact ID", "🔢 Version codée en dur"},
                    rows
            ));

            report.append(renderer.renderParagraph("💡 Conseil : remplace chaque version par une propriété Maven définie dans la section `<properties>` du parent."));
        }

        return report.toString();
    }

    /**
     * Retourne la liste des dépendances ayant une version non dynamique (non ${...}).
     */
    private List<Dependency> getHardcodedDependencies(MavenProject project) {
        List<Dependency> hardcodedDeps = new ArrayList<>();

        if (project.getOriginalModel().getDependencies() != null) {
            for (Dependency dep : project.getOriginalModel().getDependencies()) {
                String version = dep.getVersion();
                if (version != null && !version.trim().isEmpty() && !version.trim().startsWith("${")) {
                    hardcodedDeps.add(dep);
                }
            }
        }

        return hardcodedDeps;
    }
}