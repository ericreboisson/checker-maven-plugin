package org.elitost.maven.plugins.checkers;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.ModelBase;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class OutdatedDependenciesChecker implements CustomChecker, InitializableChecker, AutoCloseable {

    private static final int DEFAULT_TIMEOUT = 30;
    private static final String DEFAULT_IGNORE_SCOPES = "system,test,provided";

    private Log log;
    private RepositorySystem repoSystem;
    private RepositorySystemSession session;
    private List<RemoteRepository> remoteRepositories;
    private ReportRenderer renderer;

    private final boolean skip;
    private final Set<String> ignoreScopes;
    private final Pattern ignoreGroupsPattern;
    private final int timeoutSeconds;
    private final boolean showAll;
    private ExecutorService executorService;

    public OutdatedDependenciesChecker() {
        this.skip = false;
        this.ignoreScopes = new HashSet<>(Arrays.asList(DEFAULT_IGNORE_SCOPES.split(",")));
        this.ignoreGroupsPattern = null;
        this.timeoutSeconds = DEFAULT_TIMEOUT;
        this.showAll = false;
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
        this.executorService = Executors.newWorkStealingPool();
    }

    @Override
    public String getId() {
        return "outdatedDependencies";
    }

    @Override
    public String generateReport(CheckerContext checkerContext) {
        if (skip) {
            log.info("[OutdatedDependenciesChecker] Checker désactivé par configuration");
            return "";
        }

        List<Dependency> dependencies = Optional.ofNullable(checkerContext.getCurrentModule())
                .map(MavenProject::getOriginalModel)
                .map(ModelBase::getDependencies)
                .orElse(Collections.emptyList());

        if (dependencies.isEmpty()) {
            log.info("Aucune dépendance à vérifier.");
            return "";
        }

        List<DependencyInfo> results = checkDependenciesConcurrently(dependencies);
        return buildReport(results);
    }

    private List<DependencyInfo> checkDependenciesConcurrently(List<Dependency> dependencies) {
        List<Future<DependencyInfo>> futures = dependencies.stream()
                .filter(this::shouldCheckDependency)
                .map(dep -> executorService.submit(() -> checkDependencyVersion(dep)))
                .collect(Collectors.toList());

        return collectResults(futures);
    }

    private boolean shouldCheckDependency(Dependency dep) {
        if (dep.getVersion() == null || dep.getVersion().trim().isEmpty()) {
            return false;
        }
        if (dep.getVersion().startsWith("${")) {
            log.debug("Version dynamique ignorée: " + dep.getManagementKey());
            return false;
        }
        if (dep.getScope() != null && ignoreScopes.contains(dep.getScope())) {
            return false;
        }
        return ignoreGroupsPattern == null || !ignoreGroupsPattern.matcher(dep.getGroupId()).matches();
    }

    private DependencyInfo checkDependencyVersion(Dependency dep) {
        try {
            VersionRangeRequest rangeRequest = new VersionRangeRequest();
            rangeRequest.setArtifact(new DefaultArtifact(
                    dep.getGroupId(),
                    dep.getArtifactId(),
                    "jar",
                    "[" + dep.getVersion() + ",)"
            ));
            rangeRequest.setRepositories(remoteRepositories);

            VersionRangeResult result = repoSystem.resolveVersionRange(session, rangeRequest);

            return new DependencyInfo(
                    dep.getGroupId(),
                    dep.getArtifactId(),
                    dep.getVersion(),
                    findLatestStableVersion(result.getVersions()),
                    findLatestVersion(result.getVersions())
            );
        } catch (Exception e) {
            log.warn(String.format("Erreur vérification %s:%s:%s - %s",
                    dep.getGroupId(),
                    dep.getArtifactId(),
                    dep.getVersion(),
                    e.getMessage()));
            return new DependencyInfo(
                    dep.getGroupId(),
                    dep.getArtifactId(),
                    dep.getVersion(),
                    null,
                    null
            );
        }
    }

    private String findLatestStableVersion(Collection<Version> versions) {
        return Optional.ofNullable(versions).flatMap(v -> v.stream()
                        .filter(ver -> ver != null && !ver.toString().toUpperCase().contains("SNAPSHOT"))
                        .max(Comparator.naturalOrder())
                        .map(Version::toString))
                .orElse(null);
    }

    private String findLatestVersion(Collection<Version> versions) {
        return Optional.ofNullable(versions).flatMap(v -> v.stream()
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .map(Version::toString))
                .orElse(null);
    }

    private List<DependencyInfo> collectResults(List<Future<DependencyInfo>> futures) {
        return futures.stream()
                .map(f -> {
                    try {
                        return f.get(timeoutSeconds, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        log.warn("Timeout lors de la vérification d'une dépendance");
                        return null;
                    } catch (Exception e) {
                        log.warn("Erreur lors du traitement d'une dépendance", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private String buildReport(List<DependencyInfo> results) {
        List<DependencyInfo> outdated = results.stream()
                .filter(info -> info.latestStable != null && !info.latestStable.equals(info.currentVersion))
                .sorted(Comparator.comparing(DependencyInfo::getGroupId))
                .collect(Collectors.toList());

        List<DependencyInfo> upToDate = showAll ?
                results.stream()
                        .filter(info -> info.latestStable != null && info.latestStable.equals(info.currentVersion))
                        .sorted(Comparator.comparing(DependencyInfo::getGroupId))
                        .collect(Collectors.toList()) :
                Collections.emptyList();

        if (outdated.isEmpty() && upToDate.isEmpty()) {
            return "";
        }

        StringBuilder report = new StringBuilder();
        report.append(renderer.renderHeader3("📦 Rapport des dépendances obsolètes"));
        report.append(renderer.openIndentedSection());

        if (!outdated.isEmpty()) {
            report.append(renderer.renderWarning(
                    String.format("%d dépendance(s) obsolète(s) trouvée(s):", outdated.size())));

            String[] headers = {"Group ID", "Artifact ID", "Version actuelle", "Dernière stable"};
            String[][] rows = outdated.stream()
                    .map(info -> new String[]{
                            info.groupId,
                            info.artifactId,
                            info.currentVersion,
                            info.latestStable // Affichage simple sans formatage spécial
                    })
                    .toArray(String[][]::new);

            report.append(renderer.renderTable(headers, rows));
        }

        if (!upToDate.isEmpty()) {
            report.append(renderer.renderInfo(
                    String.format("\n%d dépendance(s) à jour:", upToDate.size())));

            String[] headers = {"Group ID", "Artifact ID", "Version"};
            String[][] rows = upToDate.stream()
                    .map(info -> new String[]{
                            info.groupId,
                            info.artifactId,
                            info.currentVersion
                    })
                    .toArray(String[][]::new);

            report.append(renderer.renderTable(headers, rows));
        }

        report.append(renderer.renderParagraph(
                "💡 Conseil: Vérifiez la compatibilité avant de mettre à jour les dépendances."));
        report.append(renderer.closeIndentedSection());

        return report.toString();
    }

    @Override
    public void close() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    private static class DependencyInfo {
        final String groupId;
        final String artifactId;
        final String currentVersion;
        final String latestStable;
        final String latest;

        DependencyInfo(String groupId, String artifactId, String currentVersion,
                       String latestStable, String latest) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.currentVersion = currentVersion;
            this.latestStable = latestStable;
            this.latest = latest;
        }

        String getGroupId() {
            return groupId;
        }
    }
}