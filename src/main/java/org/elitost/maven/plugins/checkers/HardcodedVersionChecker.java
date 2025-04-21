package org.elitost.maven.plugins.checkers;

import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;

/**
 * Vérifie que les dépendances d’un projet Maven ne contiennent pas de versions codées en dur dans le `pom.xml`.
 * Encourage l’usage de propriétés Maven pour une meilleure maintenabilité.
 */
public class HardcodedVersionChecker implements CustomChecker, InitializableChecker {

    private Log log;
    private ReportRenderer renderer;

    /** Constructeur sans argument pour l'utilisation via SPI */
    public HardcodedVersionChecker() {}

    @Override
    public void init(Log log,
                     org.eclipse.aether.RepositorySystem repoSystem,
                     org.eclipse.aether.RepositorySystemSession session,
                     List<org.eclipse.aether.repository.RemoteRepository> remoteRepositories,
                     ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    @Override
    public String getId() {
        return "hardcodedVersion";
    }

    /**
     * Génère un rapport listant les dépendances dont les versions sont codées en dur.
     */
    @Override
    public String generateReport(CheckerContext checkerContext) {
        List<Dependency> hardcodedDeps = findHardcodedDependencies(checkerContext.getCurrentModule());

        if (hardcodedDeps.isEmpty()) {
            return "";
        }

        StringBuilder report = new StringBuilder();
        String artifactId = checkerContext.getCurrentModule().getArtifactId();

        report.append(renderer.renderHeader3("🧱 Versions codées en dur détectées dans `" + artifactId + "`"));
        report.append(renderer.openIndentedSection());
        report.append(renderer.renderError(
                "Les dépendances suivantes utilisent une version définie en dur dans le `pom.xml`, au lieu d’une propriété `${...}`.\n" +
                        "Cela nuit à la centralisation et à la maintenabilité des versions."));

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
                "💡 Conseil : remplace chaque version codée en dur par une propriété Maven définie dans la section `<properties>` du parent."));
        report.append(renderer.closeIndentedSection());

        return report.toString();
    }

    /**
     * Recherche les dépendances définies avec une version codée en dur (non dynamique).
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