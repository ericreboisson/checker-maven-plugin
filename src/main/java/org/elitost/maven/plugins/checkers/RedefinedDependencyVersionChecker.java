package org.elitost.maven.plugins.checkers;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.elitost.maven.plugins.renderers.ReportRenderer;

import java.util.*;

public class RedefinedDependencyVersionChecker {

    private final Log log;
    private final ReportRenderer renderer;

    public RedefinedDependencyVersionChecker(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    public String generateRedefinitionReport(MavenProject project) {
        StringBuilder report = new StringBuilder();

        try {
            Map<String, String> inheritedVersions = getManagedDependencyVersions(project.getParent());
            List<String[]> redefined = new ArrayList<>();

            for (Dependency dep : project.getDependencies()) {
                String key = dep.getGroupId() + ":" + dep.getArtifactId();
                String declaredVersion = dep.getVersion();
                String inheritedVersion = inheritedVersions.get(key);

                if (declaredVersion != null && inheritedVersion != null && !declaredVersion.equals(inheritedVersion)) {
                    redefined.add(new String[]{
                            key,
                            inheritedVersion,
                            declaredVersion
                    });

                    log.warn("[RedefinedDependencyVersionChecker] 🔁 " + key + " redéfini : " + inheritedVersion + " ➝ " + declaredVersion);
                }
            }

            if (!redefined.isEmpty()) {
                report.append(renderer.renderHeader3("🔁 Dépendances redéfinies dans `" + project.getArtifactId() + "`"));
                report.append(renderer.openIndentedSection());

                report.append(renderer.renderParagraph("⚠️ Certaines dépendances redéfinissent une version différente de celle héritée :"));
                report.append(renderer.renderTable(
                        new String[]{"Dépendance", "Version héritée", "Version redéfinie"},
                        redefined.toArray(new String[0][0])
                ));
            }

        } catch (Exception e) {
            log.error("[RedefinedDependencyVersionChecker] Exception levée", e);
            report.append(renderer.renderError("❌ Une erreur est survenue : " + e.getMessage()));
        }
        report.append(renderer.closeIndentedSection());

        return report.toString();
    }

    private Map<String, String> getManagedDependencyVersions(MavenProject parent) {
        if (parent == null) return Collections.emptyMap();
        DependencyManagement depMgmt = parent.getDependencyManagement();
        if (depMgmt == null) return Collections.emptyMap();

        Map<String, String> versions = new HashMap<>();
        for (Dependency dep : depMgmt.getDependencies()) {
            if (dep.getVersion() != null) {
                String key = dep.getGroupId() + ":" + dep.getArtifactId();
                versions.put(key, dep.getVersion());
            }
        }
        return versions;
    }
}