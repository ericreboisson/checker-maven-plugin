package com.example.modulechecker.checkers;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class UnusedDependenciesChecker {
    private final Log log;

    public UnusedDependenciesChecker(Log log) {
        this.log = log;
    }

    public String generateUnusedDependenciesReport(MavenProject project, String mavenExecutable) {
        StringBuilder report = new StringBuilder();

        try {
            log.info("🔍 Analyse des dépendances non utilisées pour " + project.getArtifactId());

            Process process = Runtime.getRuntime().exec(
                    mavenExecutable + " dependency:analyze -f " + project.getFile().getAbsolutePath());

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            boolean inUnusedBlock = false;
            StringBuilder unusedBlock = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Unused declared dependencies found:")) {
                    inUnusedBlock = true;
                } else if (line.startsWith("[INFO]") && inUnusedBlock && line.trim().equals("[INFO]")) {
                    inUnusedBlock = false;
                } else if (inUnusedBlock) {
                    unusedBlock.append(line.replaceFirst("^\\[INFO\\]\\s*", "")).append("\n");
                }
            }

            int exitCode = process.waitFor();

            if (unusedBlock.length() > 0) {
                report.append("❗ **Dépendances inutilisées dans `")
                      .append(project.getArtifactId())
                      .append("`** :\n\n```text\n")
                      .append(unusedBlock)
                      .append("```\n");

                if (exitCode != 0) {
                    log.warn("⚠️ dependency:analyze a retourné un code " + exitCode + ", mais des dépendances inutilisées ont été trouvées.");
                }
            } else if (exitCode != 0) {
                log.warn("⚠️ dependency:analyze a échoué sans résultat exploitable pour " + project.getArtifactId());
            }

        } catch (Exception e) {
            log.error("❌ Erreur pendant l'analyse des dépendances inutilisées : " + e.getMessage(), e);
        }

        return report.toString();
    }
}