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
import java.util.Optional;

/**
 * Vérifie si la version du parent dans un fichier pom.xml est à jour.
 * Compare la version déclarée avec la dernière version stable disponible dans les repositories Maven.
 */
public class ParentVersionChecker implements CustomChecker, InitializableChecker {

    private static final String POM_EXTENSION = "pom";

    private Log log;
    private RepositorySystem repoSystem;
    private RepositorySystemSession session;
    private List<RemoteRepository> remoteRepositories;
    private ReportRenderer renderer;

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
        try {
            return Optional.ofNullable(checkerContext.getCurrentModule())
                    .map(module -> checkParentVersion(module.getFile(), module.getName()))
                    .orElse("");
        } catch (Exception e) {
            log.error("Erreur lors de la vérification du parent", e);
            return renderer.renderError("Erreur lors de la vérification du parent : " + e.getMessage());
        }
    }

    private String checkParentVersion(File pomFile, String moduleName) {
        if (pomFile == null || !pomFile.exists()) {
            log.warn("Fichier pom.xml introuvable pour le projet " + moduleName);
            return "";
        }

        try (FileReader reader = new FileReader(pomFile)) {
            Model model = new MavenXpp3Reader().read(reader);
            Parent parent = model.getParent();

            if (parent == null) {
                return renderNoParentWarning(moduleName);
            }

            return checkParentVersion(parent);
        } catch (Exception e) {
            log.error("Erreur lors de l'analyse du parent dans le pom.xml du projet " + moduleName, e);
            return renderer.renderError("Erreur lors de l'analyse du parent : " + e.getMessage());
        }
    }

    private String renderNoParentWarning(String moduleName) {
        log.error("Aucun parent défini pour le projet " + moduleName);

        StringBuilder report = new StringBuilder();
        report.append(renderer.renderHeader3("👪 Absence de parent Maven détectée"));
        report.append(renderer.openIndentedSection());
        report.append(renderer.renderError("Aucun parent défini pour le projet `" + moduleName + "`."));
        report.append(renderer.renderInfo("Si ce module est censé hériter d'un parent, vérifiez la configuration de l'élément <parent>."));
        report.append(renderer.closeIndentedSection());

        return report.toString();
    }

    private String checkParentVersion(Parent parent) {
        String currentVersion = parent.getVersion();
        String latestVersion = getLatestParentVersion(parent);

        if (latestVersion == null) {
            log.warn("Impossible de déterminer la dernière version pour le parent: "
                    + parent.getGroupId() + ":" + parent.getArtifactId());
            return "";
        }

        if (!Objects.equals(latestVersion, currentVersion)) {
            return renderOutdatedParentWarning(parent, currentVersion, latestVersion);
        }

        log.info("Version du parent à jour: " + parent.getGroupId() + ":"
                + parent.getArtifactId() + ":" + currentVersion);
        return "";
    }

    private String renderOutdatedParentWarning(Parent parent, String currentVersion, String latestVersion) {
        StringBuilder report = new StringBuilder();
        report.append(renderer.renderHeader3("👪 Version obsolète du parent détectée"));
        report.append(renderer.openIndentedSection());

        report.append(renderer.renderWarning("Le parent déclaré n'utilise pas la dernière version disponible."));

        String[] headers = {"Group ID", "Artifact ID", "Version actuelle", "Dernière version stable"};
        String[][] rows = {{parent.getGroupId(), parent.getArtifactId(), currentVersion, latestVersion}};
        report.append(renderer.renderTable(headers, rows));

        report.append(renderer.renderParagraph("💡 Mettez à jour la version du parent pour bénéficier des dernières améliorations."));
        report.append(renderer.closeIndentedSection());

        return report.toString();
    }

    private String getLatestParentVersion(Parent parent) {
        try {
            DefaultArtifact artifact = new DefaultArtifact(
                    parent.getGroupId(),
                    parent.getArtifactId(),
                    POM_EXTENSION,
                    "[" + parent.getVersion() + ",)"
            );

            VersionRangeRequest request = new VersionRangeRequest(artifact, remoteRepositories, null);
            VersionRangeResult result = repoSystem.resolveVersionRange(session, request);

            return result.getVersions().stream()
                    .filter(Objects::nonNull)
                    .filter(v -> !v.toString().contains("SNAPSHOT"))
                    .max(Comparator.naturalOrder())
                    .map(Version::toString)
                    .orElse(null);

        } catch (Exception e) {
            log.warn("Impossible de récupérer la dernière version pour le parent: "
                    + parent.getGroupId() + ":" + parent.getArtifactId(), e);
            return null;
        }
    }
}