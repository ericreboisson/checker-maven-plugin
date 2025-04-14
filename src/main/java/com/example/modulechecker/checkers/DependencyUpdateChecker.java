package com.example.modulechecker.checkers;

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

    public DependencyUpdateChecker(Log log, RepositorySystem repoSystem,
                                   RepositorySystemSession session,
                                   List<RemoteRepository> remoteRepositories) {
        this.log = log;
        this.repoSystem = repoSystem;
        this.session = session;
        this.remoteRepositories = remoteRepositories;
    }


    /**
     * Vérifie si une version plus récente est disponible pour les dépendances d'un module
     * et génère un rapport Markdown des dépendances obsolètes.
     */
    public String generateOutdatedDependenciesReport(List<Dependency> dependencies) {
        StringBuilder report = new StringBuilder();
        List<String> outdatedDependencies = checkForUpdates(dependencies);

        if (!outdatedDependencies.isEmpty()) {
            report.append("❗ **Dépendances obsolètes** :\n\n");
            report.append("| GroupId | ArtifactId | Version actuelle | Version la plus récente |\n|---------|------------|-------------------|-------------------------|\n");

            for (String line : outdatedDependencies) {
                String[] parts = line.split("[:\\[\\]➔]+");
                report.append(String.format("| %s | %s | %s | %s |\n", parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim()));
            }
        }
        return report.toString();
    }
    public List<String> checkForUpdates(List dependencies) {
        List<String> outdatedDeps = new ArrayList<>();

        for (Object dependency : dependencies) {
            if (dependency instanceof Dependency dep) {
                try {
                    if (dep.getVersion() == null || dep.getVersion().startsWith("${")) {
                        continue; // skip properties or null versions
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
                    List<Version> versions = result.getVersions();

                    Version latest = versions.stream()
                            .filter(v -> !v.toString().contains("SNAPSHOT"))
                            .max(Comparator.naturalOrder())
                            .orElse(null);

                    if (latest != null && !latest.toString().equals(dep.getVersion())) {
                        outdatedDeps.add(String.format("%s:%s [%s ➔ %s]",
                                dep.getGroupId(), dep.getArtifactId(),
                                dep.getVersion(), latest));
                    }

                } catch (Exception e) {
                    log.warn("Impossible de vérifier " + dep.getGroupId() + ":" + dep.getArtifactId(), e);
                }
            }

        }

        return outdatedDeps;
    }
}