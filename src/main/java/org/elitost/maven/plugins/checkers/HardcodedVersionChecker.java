package org.elitost.maven.plugins.checkers;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Vérifie que les dépendances et plugins d'un projet Maven ne contiennent pas de versions codées en dur.
 * Encourage l'usage de propriétés Maven pour une meilleure maintenabilité.
 */
public class HardcodedVersionChecker implements CustomChecker, InitializableChecker {

    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{.+?}");
    private static final String SCOPES_TO_IGNORE = "test|provided|system|import";

    private Log log;
    private ReportRenderer renderer;
    private boolean checkPlugins;
    private boolean ignoreOptionalDeps;
    private boolean ignoreSpecificScopes;

    public HardcodedVersionChecker() {
    }

    @Override
    public void init(Log log,
                     RepositorySystem repoSystem,
                     RepositorySystemSession session,
                     List<RemoteRepository> remoteRepositories,
                     ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
        // Configuration par défaut, pourrait être externalisée
        this.checkPlugins = true;
        this.ignoreOptionalDeps = true;
        this.ignoreSpecificScopes = true;
    }

    @Override
    public String getId() {
        return "hardcodedVersion";
    }

    @Override
    public String generateReport(CheckerContext checkerContext) {
        MavenProject project = checkerContext.getCurrentModule();
        List<Dependency> hardcodedDeps = findHardcodedDependencies(project);
        List<Plugin> hardcodedPlugins = checkPlugins ? findHardcodedPlugins(project) : List.of();

        if (hardcodedDeps.isEmpty() && hardcodedPlugins.isEmpty()) {
            return "";
        }

        StringBuilder report = new StringBuilder();
        String artifactId = project.getArtifactId();

        report.append(renderer.renderHeader3("🧱 Versions codées en dur détectées dans " + artifactId));
        report.append(renderer.openIndentedSection());

        if (!hardcodedDeps.isEmpty()) {
            appendDependenciesReport(report, hardcodedDeps);
        }

        if (!hardcodedPlugins.isEmpty()) {
            appendPluginsReport(report, hardcodedPlugins);
        }

        report.append(renderer.renderParagraph(
                "💡 Conseil : remplacez les versions codées en dur par des propriétés Maven " +
                        "définies dans la section <properties> du parent."));
        report.append(renderer.closeIndentedSection());

        return report.toString();
    }

    private void appendDependenciesReport(StringBuilder report, List<Dependency> hardcodedDeps) {
        report.append(renderer.renderError(
                "Les dépendances suivantes utilisent une version définie en dur dans le pom.xml :\n"));

        String[] headers = {"🏷️ Group ID", "📘 Artifact ID", "🔢 Version", "🎯 Scope"};
        String[][] rows = hardcodedDeps.stream()
                .map(dep -> {
                    log.warn(String.format("[%s] Version codée en dur: %s:%s:%s (scope: %s)",
                            getId(), dep.getGroupId(), dep.getArtifactId(), dep.getVersion(),
                            dep.getScope()));

                    return new String[]{
                            dep.getGroupId(),
                            dep.getArtifactId(),
                            renderer.renderWarning(dep.getVersion()),
                            dep.getScope() != null ? dep.getScope() : "compile"
                    };
                })
                .toArray(String[][]::new);

        report.append(renderer.renderTable(headers, rows));
    }

    private void appendPluginsReport(StringBuilder report, List<Plugin> hardcodedPlugins) {
        report.append(renderer.renderError(
                "\nLes plugins suivants utilisent une version définie en dur dans le pom.xml :\n"));

        String[] headers = {"🏷️ Group ID", "📘 Artifact ID", "🔢 Version"};
        String[][] rows = hardcodedPlugins.stream()
                .map(plugin -> {
                    log.warn(String.format("[%s] Plugin version codée en dur: %s:%s:%s",
                            getId(), plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion()));

                    return new String[]{
                            plugin.getGroupId(),
                            plugin.getArtifactId(),
                            renderer.renderWarning(plugin.getVersion())
                    };
                })
                .toArray(String[][]::new);

        report.append(renderer.renderTable(headers, rows));
    }

    private List<Dependency> findHardcodedDependencies(MavenProject project) {
        List<Dependency> result = new ArrayList<>();

        List<Dependency> declaredDependencies = project.getOriginalModel().getDependencies();
        if (declaredDependencies == null || declaredDependencies.isEmpty()) {
            return result;
        }

        for (Dependency dependency : declaredDependencies) {
            if (shouldSkipDependency(dependency)) {
                continue;
            }

            String version = dependency.getVersion();
            if (isHardcoded(version)) {
                result.add(dependency);
            }
        }

        return result;
    }

    private boolean shouldSkipDependency(Dependency dependency) {
        // Ignorer les dépendances sans version
        if (dependency.getVersion() == null || dependency.getVersion().trim().isEmpty()) {
            return true;
        }

        // Ignorer les dépendances optionnelles si configurées
        if (ignoreOptionalDeps && dependency.isOptional()) {
            return true;
        }

        // Ignorer certains scopes si configuré
        return ignoreSpecificScopes && dependency.getScope() != null
                && SCOPES_TO_IGNORE.contains(dependency.getScope());
    }

    private List<Plugin> findHardcodedPlugins(MavenProject project) {
        List<Plugin> result = new ArrayList<>();

        if (project.getOriginalModel().getBuild() == null
                || project.getOriginalModel().getBuild().getPlugins() == null) {
            return result;
        }

        for (Plugin plugin : project.getOriginalModel().getBuild().getPlugins()) {
            String version = plugin.getVersion();
            if (isHardcoded(version)) {
                result.add(plugin);
            }
        }

        return result;
    }

    private boolean isHardcoded(String version) {
        return version != null
                && !version.trim().isEmpty()
                && !PROPERTY_PATTERN.matcher(version.trim()).matches();
    }
}