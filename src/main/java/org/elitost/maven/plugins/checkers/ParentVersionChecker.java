package org.elitost.maven.plugins.checkers;

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;

import java.io.File;
import java.io.FileReader;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Vérifie si la version du parent dans un fichier pom.xml est à jour.
 * Compare la version déclarée avec la dernière version stable disponible dans les repositories Maven.
 */
public class ParentVersionChecker implements CustomChecker, InitializableChecker {

    private Log log;
    private RepositorySystem repoSystem;
    private RepositorySystemSession session;
    private List<RemoteRepository> remoteRepositories;
    private ReportRenderer renderer;

    /** Constructeur par défaut pour SPI */
    public ParentVersionChecker() {}

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
        return "parentVersion";
    }

    @Override
    public String generateReport(CheckerContext checkerContext) {
        File pomFile = checkerContext.getCurrentModule().getFile();
        if (pomFile == null || !pomFile.exists()) {
            log.warn("Fichier pom.xml introuvable pour le projet " + checkerContext.getCurrentModule().getName());
            return "";
        }

        try (FileReader reader = new FileReader(pomFile)) {
            Model model = new MavenXpp3Reader().read(reader);
            Parent parent = model.getParent();

            if (parent == null) {
                log.error("Aucun parent défini pour le projet " + checkerContext.getCurrentModule().getName());

                StringBuilder report = new StringBuilder();
                report.append(renderer.renderHeader3("👪 Absence de parent Maven détectée"));
                report.append(renderer.openIndentedSection());
                report.append(renderer.renderError("Aucun parent défini pour le projet `" + checkerContext.getCurrentModule().getName() + "`."));
                report.append(renderer.renderInfo("Si ce module est censé hériter d’un `pom` parent, vérifiez la configuration de l’élément `<parent>`."));
                report.append(renderer.closeIndentedSection());

                return report.toString();
            }

            return renderIfOutdated(parent);

        } catch (Exception e) {
            log.error("Erreur lors de l'analyse du parent dans le pom.xml du projet " + checkerContext.getCurrentModule().getName(), e);
            return renderer.renderError("Erreur lors de l'analyse du parent : " + e.getMessage());
        }
    }

    private String renderIfOutdated(Parent parent) {
        String currentVersion = parent.getVersion();
        String latestVersion = getLatestParentVersion(parent);

        if (latestVersion != null && !Objects.equals(latestVersion, currentVersion)) {
            StringBuilder report = new StringBuilder();
            report.append(renderer.renderHeader3("👪 Version obsolète du parent détectée"));
            report.append(renderer.openIndentedSection());

            report.append(renderer.renderWarning("Le fichier `pom.xml` utilise une version du parent qui n'est pas la plus récente disponible."));

            String[] headers = {"🏷️ Group ID", "📘 Artifact ID", "🕒 Version actuelle", "🚀 Dernière version stable"};
            String[][] rows = {{parent.getGroupId(), parent.getArtifactId(), currentVersion, latestVersion}};
            report.append(renderer.renderTable(headers, rows));
            report.append(renderer.renderParagraph("💡 Pensez à mettre à jour la version du parent pour bénéficier des dernières améliorations."));

            report.append(renderer.closeIndentedSection());
            return report.toString();
        }

        return ""; // Version actuelle à jour
    }

    public String getLatestParentVersion(Parent parent) {
        try {
            DefaultArtifact artifact = new DefaultArtifact(
                    parent.getGroupId(),
                    parent.getArtifactId(),
                    "pom",
                    "[" + parent.getVersion() + ",)"
            );

            VersionRangeRequest request = new VersionRangeRequest(artifact, remoteRepositories, null);
            VersionRangeResult result = repoSystem.resolveVersionRange(session, request);

            return result.getVersions().stream()
                    .filter(v -> !v.toString().contains("SNAPSHOT"))
                    .max(Comparator.naturalOrder())
                    .map(Version::toString)
                    .orElse(null);

        } catch (Exception e) {
            log.warn("Impossible de récupérer la dernière version pour le parent : " +
                    parent.getGroupId() + ":" + parent.getArtifactId(), e);
            return null;
        }
    }
}