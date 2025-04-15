// === ParentVersionChecker modifié ===
package com.example.modulechecker.checkers;

import com.example.modulechecker.renderers.ReportRenderer;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;

import java.io.File;
import java.io.FileReader;
import java.util.Comparator;
import java.util.List;

public class ParentVersionChecker {
    private final Log log;
    private final RepositorySystem repoSystem;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> remoteRepositories;
    private final ReportRenderer renderer;

    public ParentVersionChecker(Log log,
                                RepositorySystem repoSystem,
                                RepositorySystemSession session,
                                List<RemoteRepository> remoteRepositories, ReportRenderer renderer) {
        this.log = log;
        this.repoSystem = repoSystem;
        this.session = session;
        this.remoteRepositories = remoteRepositories;
        this.renderer = renderer;
    }

    public String generateParentVersionReport(MavenProject project) {
        StringBuilder report = new StringBuilder();

        try {
            File pomFile = project.getFile();
            if (pomFile == null || !pomFile.exists()) {
                log.warn("❌ Fichier pom.xml introuvable pour le projet " + project.getName());
                return "";
            }

            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model = reader.read(new FileReader(pomFile));
            Parent parent = model.getParent();

            if (parent == null) {
                log.info("ℹ️ Aucun parent défini pour le projet " + project.getName());
                return renderer.renderInfo("Aucun parent défini pour le projet " + project.getName());
            }

            String currentVersion = parent.getVersion();
            String latestVersion = getLatestParentVersion(parent);

            if (latestVersion != null && !latestVersion.equals(currentVersion)) {
                report.append(renderer.renderTitle("👪 Version obsolète du parent détectée"));
                report.append(renderer.renderParagraph("Le fichier `pom.xml` utilise une version du parent qui n'est pas la plus récente disponible."));

                String[] headers = {"🏷️ Group ID", "📘 Artifact ID", "🕒 Version actuelle", "🚀 Dernière version stable"};
                String[][] rows = {{parent.getGroupId(), parent.getArtifactId(), currentVersion, latestVersion}};
                report.append(renderer.renderTable(headers, rows));
                report.append(renderer.renderWarning("Pensez à mettre à jour la version du parent pour bénéficier des dernières améliorations."));
            }
        } catch (Exception e) {
            log.error("❌ Erreur lors de l'analyse du parent dans le pom.xml", e);
            report.append(renderer.renderError("Erreur lors de l'analyse du parent : " + e.getMessage()));
        }

        return report.toString();
    }

    public String getLatestParentVersion(Parent parent) {
        try {
            DefaultArtifact artifact = new DefaultArtifact(
                parent.getGroupId(),
                parent.getArtifactId(),
                "pom",
                "[" + parent.getVersion() + ",)"
            );

            VersionRangeRequest request = new VersionRangeRequest();
            request.setArtifact(artifact);
            request.setRepositories(remoteRepositories);

            VersionRangeResult result = repoSystem.resolveVersionRange(session, request);
            List<Version> versions = result.getVersions();

            return versions.stream()
                    .filter(v -> !v.toString().contains("SNAPSHOT"))
                    .max(Comparator.naturalOrder())
                    .map(Version::toString)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("❌ Impossible de récupérer la dernière version pour le parent : " + parent.getGroupId() + ":" + parent.getArtifactId(), e);
            return null;
        }
    }
}