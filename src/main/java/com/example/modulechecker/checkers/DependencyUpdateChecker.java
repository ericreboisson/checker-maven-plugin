package com.example.modulechecker.checkers;

import com.example.modulechecker.renderers.ReportRenderer;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DependencyUpdateChecker {

    private final Log log;
    private final RepositorySystem repoSystem;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> remoteRepositories;
    private final ReportRenderer renderer;

    public DependencyUpdateChecker(Log log, RepositorySystem repoSystem,
                                   RepositorySystemSession session,
                                   List<RemoteRepository> remoteRepositories,
                                   ReportRenderer renderer) {
        this.log = log;
        this.repoSystem = repoSystem;
        this.session = session;
        this.remoteRepositories = remoteRepositories;
        this.renderer = renderer;
    }

    public String generateOutdatedDependenciesReport(List<Dependency> dependencies) {
        StringBuilder report = new StringBuilder();
        List<String[]> outdated = checkForUpdates(dependencies);

        if (!outdated.isEmpty()) {
            report.append(renderer.renderTitle("📦 Dépendances obsolètes détectées"));
            report.append(renderer.renderParagraph("Certaines dépendances ont une version plus récente disponible dans les dépôts Maven. Il est recommandé de les mettre à jour pour bénéficier des dernières corrections de bugs, améliorations et correctifs de sécurité."));

            String[] headers = { "🏷️ Group ID", "📘 Artifact ID", "🕒 Version actuelle", "🚀 Dernière version stable" };
            String[][] rows = outdated.toArray(new String[0][]);

            report.append(renderer.renderTable(headers, rows));
            report.append(renderer.renderInfo("🔄 Pensez à tester les mises à jour avant de les intégrer définitivement."));
        }

        return report.toString();
    }

    private List<String[]> checkForUpdates(List<Dependency> dependencies) {
        List<String[]> outdatedDeps = new ArrayList<>();

        for (Dependency dep : dependencies) {
            try {
                if (dep.getVersion() == null || dep.getVersion().startsWith("${")) {
                    continue;
                }

                DefaultArtifact artifact = new DefaultArtifact(
                        dep.getGroupId(),
                        dep.getArtifactId(),
                        "jar",
                        "[" + dep.getVersion() + ",)"
                );

                VersionRangeRequest rangeRequest = new VersionRangeRequest();
                rangeRequest.setArtifact(artifact);
                rangeRequest.setRepositories(remoteRepositories);

                VersionRangeResult result = repoSystem.resolveVersionRange(session, rangeRequest);
                Version latest = result.getVersions().stream()
                        .filter(v -> !v.toString().contains("SNAPSHOT"))
                        .max(Comparator.naturalOrder())
                        .orElse(null);

                if (latest != null && !latest.toString().equals(dep.getVersion())) {
                    outdatedDeps.add(new String[] {
                            dep.getGroupId(),
                            dep.getArtifactId(),
                            dep.getVersion(),
                            latest.toString()
                    });
                }

            } catch (Exception e) {
                log.warn("❌ Impossible de vérifier " + dep.getGroupId() + ":" + dep.getArtifactId(), e);
            }
        }

        return outdatedDeps;
    }
}