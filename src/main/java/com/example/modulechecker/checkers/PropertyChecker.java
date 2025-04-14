// PropertyChecker.java
package com.example.modulechecker.checkers;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.util.List;
import java.util.Properties;

public class PropertyChecker {

    private final Log log;

    public PropertyChecker(Log log) {
        this.log = log;
    }

    public String generatePropertiesCheckReport(MavenProject project, List<String> propertiesToCheck) {
        StringBuilder markdownContent = new StringBuilder();
        markdownContent.append("\n## Vérification des Propriétés\n");

        try {
            Properties props = project.getProperties();
            for (String key : propertiesToCheck) {
                if (props.containsKey(key)) {
                    markdownContent.append("- ✅ Propriété `" + key + "` est présente.\n");
                    log.info("✅ Propriété présente : " + key);
                } else {
                    markdownContent.append("- ❌ Propriété `" + key + "` est manquante.\n");
                    log.warn("❌ Propriété manquante : " + key);
                }
            }
        } catch (Exception e) {
            markdownContent.append("- ❌ Une erreur est survenue lors de la vérification des propriétés.\n");
            log.warn("❌ Erreur dans la vérification des propriétés : " + e.getMessage());
        }

        return markdownContent.toString();
    }
}