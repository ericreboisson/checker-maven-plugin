package com.example.modulechecker.checkers;

import org.apache.maven.project.MavenProject;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ModuleChecker {

    private Log log;

    public ModuleChecker(Log log) {
        this.log = log;
    }

    /**
     * Vérifie les modules présents dans le projet et génère le rapport Markdown.
     * @param project Le projet Maven.
     * @return Le contenu Markdown du rapport de vérification des modules.
     */
    public String generateModuleCheckReport(MavenProject project) {
        StringBuilder markdownContent = new StringBuilder();
        markdownContent.append("## Vérification des Modules\n");

        try {
            List<String> missingModules = checkModules(project);
            if (missingModules.isEmpty()) {
                markdownContent.append("- ✅ Tous les modules attendus sont présents.\n");
                log.info("✅ Tous les modules attendus sont présents.");
            } else {
                markdownContent.append("- ❌ Certains modules sont manquants :\n");
                for (String missingModule : missingModules) {
                    markdownContent.append("  - " + missingModule + "\n");
                    log.warn("Module manquant : " + missingModule);
                }
            }
        } catch (Exception e) {
            markdownContent.append("- ❌ Une erreur est survenue lors de la vérification des modules.\n");
            log.warn("❌ Erreur dans la vérification des modules : " + e.getMessage());
        }

        return markdownContent.toString();
    }

    /**
     * Vérifie les modules présents dans le projet.
     * @param project Le projet Maven.
     * @return Une liste des modules manquants.
     */
    private List<String> checkModules(MavenProject project) {
        String[] expectedModules = {"api", "impl", "local"};
        List<String> missingModules = new ArrayList<>();

        for (String module : expectedModules) {
            String moduleName = project.getArtifactId() + "-" + module;
            if (!isModulePresent(project, moduleName)) {
                missingModules.add(moduleName);
            }
        }

        return missingModules;
    }

    /**
     * Vérifie si un module donné est présent dans le projet.
     * @param project Le projet Maven.
     * @param moduleName Le nom du module à vérifier.
     * @return true si le module est présent, false sinon.
     */
    private boolean isModulePresent(MavenProject project, String moduleName) {
        return new File(project.getBasedir(), moduleName).exists();
    }
}