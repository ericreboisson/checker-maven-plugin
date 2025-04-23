package org.elitost.maven.plugins.checkers;

import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.project.MavenProject;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Vérifie les redéfinitions de versions de dépendances, y compris avec propriétés.
 */
public class RedefinedDependencyVersionChecker implements CustomChecker, BasicInitializableChecker {

    private static final String CHECKER_ID = "redefinedDependencyVersion";
    private static final String LOG_PREFIX = "[RedefinedDependencyVersionChecker]";

    private Log log;
    private ReportRenderer renderer;

    @Override
    public void init(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    @Override
    public String getId() {
        return CHECKER_ID;
    }

    @Override
    public String generateReport(CheckerContext checkerContext) {
        try {
            MavenProject currentProject = checkerContext.getCurrentModule();
            MavenProject parentProject = currentProject.getParent();

            // Obtenir toutes les versions résolues du parent (dépendances + propriétés)
            Map<String, String> parentResolvedVersions = getParentResolvedVersions(parentProject);

            // Obtenir les versions résolues du module courant
            Map<String, String> currentResolvedVersions = getResolvedVersions(currentProject.getDependencies(), currentProject);

            List<DependencyRedefinition> redefinitions = findVersionRedefinitions(
                    currentResolvedVersions,
                    parentResolvedVersions
            );

            return buildReport(currentProject.getArtifactId(), redefinitions);
        } catch (Exception e) {
            log.error(LOG_PREFIX + " Exception levée", e);
            return renderErrorReport(e);
        }
    }

    private Map<String, String> getParentResolvedVersions(MavenProject parent) {
        Map<String, String> versions = new HashMap<>();

        if (parent == null) {
            return versions;
        }

        // 1. Vérifier les dépendances directes du parent
        for (Dependency dep : parent.getDependencies()) {
            if (dep.getVersion() != null) {
                String key = dep.getGroupId() + ":" + dep.getArtifactId();
                String resolvedVersion = fullyResolveVersion(dep.getVersion(), parent);
                versions.put(key, resolvedVersion);
            }
        }

        // 2. Vérifier le dependencyManagement du parent
        if (parent.getDependencyManagement() != null) {
            for (Dependency dep : parent.getDependencyManagement().getDependencies()) {
                if (dep.getVersion() != null) {
                    String key = dep.getGroupId() + ":" + dep.getArtifactId();
                    String resolvedVersion = fullyResolveVersion(dep.getVersion(), parent);
                    versions.putIfAbsent(key, resolvedVersion);
                }
            }
        }

        return versions;
    }

    private Map<String, String> getResolvedVersions(List<Dependency> dependencies, MavenProject project) {
        Map<String, String> resolvedVersions = new HashMap<>();
        for (Dependency dep : dependencies) {
            if (dep.getVersion() != null && !"test".equalsIgnoreCase(dep.getScope())) {
                String key = dep.getGroupId() + ":" + dep.getArtifactId();
                String resolvedVersion = fullyResolveVersion(dep.getVersion(), project);
                resolvedVersions.put(key, resolvedVersion);
            }
        }
        return resolvedVersions;
    }

    private String fullyResolveVersion(String version, MavenProject project) {
        if (version == null) {
            return null;
        }

        // Résolution récursive des propriétés
        String resolved = version;
        int maxDepth = 10; // Prévention contre les références circulaires
        while (resolved.contains("${") && maxDepth-- > 0) {
            resolved = resolvePropertyValue(resolved, project);
        }
        return resolved;
    }

    private String resolvePropertyValue(String value, MavenProject project) {
        if (!value.contains("${")) {
            return value;
        }

        int start = value.indexOf("${");
        int end = value.indexOf("}", start);
        if (start < 0 || end < 0) {
            return value;
        }

        String propertyName = value.substring(start + 2, end);
        String propertyValue = project.getProperties().getProperty(propertyName);

        if (propertyValue == null && project.getParent() != null) {
            propertyValue = project.getParent().getProperties().getProperty(propertyName);
        }

        if (propertyValue == null) {
            propertyValue = System.getProperty(propertyName);
        }

        if (propertyValue == null) {
            return value; // Ne peut pas résoudre
        }

        return value.substring(0, start) + propertyValue + value.substring(end + 1);
    }

    private List<DependencyRedefinition> findVersionRedefinitions(
            Map<String, String> currentResolvedVersions,
            Map<String, String> parentResolvedVersions) {

        return currentResolvedVersions.entrySet().stream()
                .filter(entry -> {
                    String parentVersion = parentResolvedVersions.get(entry.getKey());
                    return parentVersion != null && !parentVersion.equals(entry.getValue());
                })
                .map(entry -> new DependencyRedefinition(
                        entry.getKey(),
                        parentResolvedVersions.get(entry.getKey()),
                        entry.getValue()
                ))
                .peek(redef -> log.warn(String.format(
                        "%s 🔁 %s redéfini : %s ➝ %s",
                        LOG_PREFIX,
                        redef.dependencyKey,
                        redef.inheritedVersion,
                        redef.redefinedVersion
                )))
                .collect(Collectors.toList());
    }

    private String buildReport(String artifactId, List<DependencyRedefinition> redefinitions) {
        if (redefinitions.isEmpty()) {
            return "";
        }

        StringBuilder report = new StringBuilder();
        report.append(renderer.renderHeader3("🔁 Dépendances redéfinies dans `" + artifactId + "`"));
        report.append(renderer.openIndentedSection());
        report.append(renderer.renderParagraph(
                "⚠️ Certaines dépendances redéfinissent une version différente de celle héritée :"
        ));

        String[][] tableData = redefinitions.stream()
                .map(redef -> new String[]{
                        redef.dependencyKey,
                        redef.inheritedVersion,
                        redef.redefinedVersion
                })
                .toArray(String[][]::new);

        report.append(renderer.renderTable(
                new String[]{"Dépendance", "Version héritée", "Version redéfinie"},
                tableData
        ));
        report.append(renderer.closeIndentedSection());

        return report.toString();
    }

    private String renderErrorReport(Exception e) {
        return renderer.renderError("❌ Une erreur est survenue : " + e.getMessage());
    }

    private static class DependencyRedefinition {
        final String dependencyKey;
        final String inheritedVersion;
        final String redefinedVersion;

        DependencyRedefinition(String dependencyKey, String inheritedVersion, String redefinedVersion) {
            this.dependencyKey = dependencyKey;
            this.inheritedVersion = inheritedVersion;
            this.redefinedVersion = redefinedVersion;
        }
    }
}