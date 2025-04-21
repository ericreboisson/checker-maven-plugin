package org.elitost.maven.plugins.checkers;

import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;
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

/**
 * Vérifie les dépendances Maven déclarées dans un projet et génère un rapport des versions obsolètes.
 */
public class OutdatedDependenciesChecker implements CustomChecker, InitializableChecker {

    private Log log;
    private RepositorySystem repoSystem;
    private RepositorySystemSession session;
    private List<RemoteRepository> remoteRepositories;
    private ReportRenderer renderer;

    public OutdatedDependenciesChecker() {
        // Constructeur vide pour instanciation via SPI
    }

    @Override
    public void init(Log log,
                     RepositorySystem repoSystem,
                     RepositorySystemSession session,
                     List<RemoteRepository> remoteRepositories,
                     ReportRenderer renderer) {
        this.log = log;
        this.repoSystem = repoSystem;
        this.session = session;
        this.remoteRepositories = remoteRepositories;
        this.renderer = renderer;
    }

    @Override
    public String getId() {
        return "outdatedDependencies";
    }

    @Override
    public String generateReport(CheckerContext checkerContext) {
        StringBuilder report = new StringBuilder();
        List<String[]> outdated = checkForUpdates(checkerContext.getCurrentModule().getOriginalModel().getDependencies());

        if (!outdated.isEmpty()) {
            report.append(renderer.renderHeader3("📦 Dépendances obsolètes détectées"));
            report.append(renderer.openIndentedSection());

            report.append(renderer.renderWarning(
                    "Certaines dépendances ont une version plus récente disponible dans les dépôts Maven. " +
                            "Il est recommandé de les mettre à jour pour bénéficier des dernières corrections de bugs, " +
                            "améliorations et correctifs de sécurité."));

            String[] headers = {"🏷️ Group ID", "📘 Artifact ID", "🕒 Version actuelle", "🚀 Dernière version stable"};
            report.append(renderer.renderTable(headers, outdated.toArray(new String[0][])));

            report.append(renderer.renderParagraph("💡 Pensez à tester les mises à jour avant de les intégrer définitivement."));
            report.append(renderer.closeIndentedSection());
        } else {
            log.info("✅ Aucune dépendance obsolète détectée.");
        }

        return report.toString();
    }

    private List<String[]> checkForUpdates(List<Dependency> dependencies) {
        List<String[]> outdatedDeps = new ArrayList<>();

        for (Dependency dep : dependencies) {
            String groupId = dep.getGroupId();
            String artifactId = dep.getArtifactId();
            String currentVersion = dep.getVersion();

            if (currentVersion == null || currentVersion.startsWith("${")) {
                log.debug("⏭️ Dépendance ignorée (version dynamique ou null) : " + groupId + ":" + artifactId);
                continue;
            }

            try {
                VersionRangeRequest rangeRequest = createVersionRangeRequest(groupId, artifactId, currentVersion);
                VersionRangeResult result = repoSystem.resolveVersionRange(session, rangeRequest);

                Version latestStable = result.getVersions().stream()
                        .filter(v -> !v.toString().contains("SNAPSHOT"))
                        .max(Comparator.naturalOrder())
                        .orElse(null);

                if (latestStable != null && !latestStable.toString().equals(currentVersion)) {
                    outdatedDeps.add(new String[]{groupId, artifactId, currentVersion, latestStable.toString()});
                }

            } catch (Exception e) {
                log.warn(String.format("❌ Impossible de vérifier les mises à jour pour %s:%s", groupId, artifactId), e);
            }
        }

        return outdatedDeps;
    }

    private VersionRangeRequest createVersionRangeRequest(String groupId, String artifactId, String currentVersion) {
        DefaultArtifact artifact = new DefaultArtifact(groupId, artifactId, "jar", "[" + currentVersion + ",)");
        VersionRangeRequest request = new VersionRangeRequest();
        request.setArtifact(artifact);
        request.setRepositories(remoteRepositories);
        return request;
    }
}