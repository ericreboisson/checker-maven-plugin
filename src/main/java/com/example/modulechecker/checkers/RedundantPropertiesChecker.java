package com.example.modulechecker.checkers;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RedundantPropertiesChecker {

    private final Log log;

    public RedundantPropertiesChecker(Log log) {
        this.log = log;
    }

    public String generateRedundantPropertiesReport(MavenProject project) {
        StringBuilder report = new StringBuilder();

        Model model = project.getOriginalModel();
        Map<String, String> properties = new HashMap<>();
        model.getProperties().forEach((key, value) -> properties.put((String) key, (String) value));

        if (properties.isEmpty()) {
            return "";
        }

        String pomAsString = model.toString();

        List<String> unusedProperties = new ArrayList<>();

        for (String key : properties.keySet()) {
            String refSyntax = "${" + key + "}";
            if (!pomAsString.contains(refSyntax)) {
                unusedProperties.add(key);
            }
        }

        if (!unusedProperties.isEmpty()) {
            report.append("❗ **Propriétés non utilisées dans `pom.xml`** :\n\n");
            report.append("| Nom de la propriété |\n");
            report.append("|----------------------|\n");
            for (String prop : unusedProperties) {
                report.append("| ").append(prop).append(" |\n");
            }
        }

        return report.toString();
    }
}