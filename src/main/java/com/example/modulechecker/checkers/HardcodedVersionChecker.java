package com.example.modulechecker.checkers;

import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.apache.maven.plugin.logging.Log;

import java.util.ArrayList;
import java.util.List;

public class HardcodedVersionChecker {

    private final Log log;

    public HardcodedVersionChecker(Log log) {
        this.log = log;
    }

    public String generateHardcodedVersionReport(MavenProject project) {
        StringBuilder report = new StringBuilder();
        List<Dependency> hardcodedDeps = getHardcodedDependencies(project);

        if (hardcodedDeps.isEmpty()) {
            report.append("✅ Aucune version codée en dur trouvée dans les dépendances du module.\n\n");
        } else {
            report.append("❌ **Dépendances avec version codée en dur** :\n\n");
            report.append("| GroupId | ArtifactId | Version |\n");
            report.append("|---------|------------|---------|\n");
            for (Dependency dep : hardcodedDeps) {
                report.append("| ")
                      .append(dep.getGroupId()).append(" | ")
                      .append(dep.getArtifactId()).append(" | ")
                      .append(dep.getVersion()).append(" |\n");
            }
            report.append("\n");
        }

        return report.toString();
    }


    private List<Dependency> getHardcodedDependencies(MavenProject project) {
        List<Dependency> hardcodedDeps = new ArrayList<>();

        if (project.getOriginalModel().getDependencies() != null) {
            for (Object obj : project.getOriginalModel().getDependencies()) {
                if (obj instanceof Dependency dep) {
                String version = dep.getVersion();
                if (version != null && !version.trim().isEmpty() && !version.trim().startsWith("${")) {
                    hardcodedDeps.add(dep);
                }
                }
            }
        }

        return hardcodedDeps;
    }
}