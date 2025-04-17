package org.elitost.maven.plugins.checkers;

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
 * <p>
 * Ce checker s'appuie sur les API Aether pour interroger les dépôts distants Maven et détecter les versions plus récentes
 * (hors SNAPSHOT) que celles actuellement utilisées.
 * </p>
 * Le rapport est produit via une instance de {@link ReportRenderer}, dans un format compatible Markdown, HTML, etc.
 *
 * @author Eric
 */
public class DependencyUpdateChecker {

    private static final String ANCHOR_ID = "dependency-updates";

    private final Log log;
    private final RepositorySystem repoSystem;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> remoteRepositories;
    private final ReportRenderer renderer;

    /**
     * Construit un checker de mise à jour des dépendances Maven.
     *
     * @param log                le logger Maven
     * @param repoSystem         le système de résolution de dépendances (Aether)
     * @param session            la session Aether courante
     * @param remoteRepositories la liste des dépôts Maven distants à interroger
     * @param renderer           le renderer pour générer les rapports
     */
    public DependencyUpdateChecker(Log log,
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

    /**
     * Génère un rapport listant les dépendances obsolètes, c’est-à-dire celles pour lesquelles une version stable
     * plus récente est disponible dans les dépôts Maven.
     *
     * @param dependencies la liste des dépendances à analyser
     * @return le contenu du rapport au format souhaité (Markdown, HTML, etc.)
     */
    public String generateOutdatedDependenciesReport(List<Dependency> dependencies) {
        StringBuilder report = new StringBuilder();
        List<String[]> outdated = checkForUpdates(dependencies);

        if (!outdated.isEmpty()) {
            report.append(renderer.renderAnchor(ANCHOR_ID));
            report.append(renderer.renderHeader3("📦 Dépendances obsolètes détectées"));
            report.append(renderer.renderParagraph(
                    "Certaines dépendances ont une version plus récente disponible dans les dépôts Maven. " +
                            "Il est recommandé de les mettre à jour pour bénéficier des dernières corrections de bugs, " +
                            "améliorations et correctifs de sécurité."));

            String[] headers = { "🏷️ Group ID", "📘 Artifact ID", "🕒 Version actuelle", "🚀 Dernière version stable" };
            report.append(renderer.renderTable(headers, outdated.toArray(new String[0][])));

            report.append(renderer.renderInfo("🔄 Pensez à tester les mises à jour avant de les intégrer définitivement."));
        } else {
            log.info("✅ Aucune dépendance obsolète détectée.");
        }

        return report.toString();
    }

    /**
     * Vérifie les dépendances fournies et détecte celles dont une version stable plus récente est disponible.
     *
     * @param dependencies la liste des dépendances à examiner
     * @return une liste de tableaux de chaînes contenant les infos : GroupId, ArtifactId, Version actuelle, Dernière version
     */
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
                    outdatedDeps.add(new String[]{ groupId, artifactId, currentVersion, latestStable.toString() });
                }

            } catch (Exception e) {
                log.warn(String.format("❌ Impossible de vérifier les mises à jour pour %s:%s", groupId, artifactId), e);
            }
        }

        return outdatedDeps;
    }

    /**
     * Crée une requête Aether pour récupérer la plage de versions disponibles pour une dépendance donnée.
     *
     * @param groupId         le groupId de la dépendance
     * @param artifactId      artifactId de la dépendance
     * @param currentVersion  la version actuelle utilisée
     * @return la requête de plage de versions
     */
    private VersionRangeRequest createVersionRangeRequest(String groupId, String artifactId, String currentVersion) {
        DefaultArtifact artifact = new DefaultArtifact(groupId, artifactId, "jar", "[" + currentVersion + ",)");
        VersionRangeRequest request = new VersionRangeRequest();
        request.setArtifact(artifact);
        request.setRepositories(remoteRepositories);
        return request;
    }
}