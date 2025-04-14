package com.example.modulechecker.checkers;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommentedTagsChecker {

    private final Log log;

    public CommentedTagsChecker(Log log) {
        this.log = log;
    }

    public String generateCommentedTagsReport(MavenProject project) {
        StringBuilder markdown = new StringBuilder();

        File pomFile = new File(project.getBasedir(), "pom.xml");
        if (!pomFile.exists()) {
            log.warn("❌ Impossible de trouver le fichier pom.xml de " + project.getArtifactId());
            return "";
        }

        try {
            String content = Files.readString(pomFile.toPath());

            // Trouve tous les blocs commentés <!-- ... -->
            Pattern commentPattern = Pattern.compile("<!--(.*?)-->", Pattern.DOTALL);
            Matcher matcher = commentPattern.matcher(content);

            boolean found = false;
            markdown.append("❗ **Balises XML Maven commentées dans `pom.xml`** :\n\n");
            markdown.append("| Contenu commenté |\n|------------------|\n");

            while (matcher.find()) {
                String commentContent = matcher.group(1).trim();

                // Vérifie si ça ressemble à une balise Maven typique
                if (commentContent.matches("(?s).*<\\s*(dependencies|dependency|build|properties|plugins|dependencyManagement|pluginManagement)[^>]*>.*")) {
                    found = true;
                    // Échappe les pipes pour éviter de casser le tableau
                    String escaped = commentContent
                            .replace("|", "\\|")
                            .replace("<", "&lt;")
                            .replace(">", "&gt;")
                            .replace("\n", "<br/>")
                            .replace("\r", "");                    markdown.append("| ").append(escaped).append(" |\n");
                }
            }

            if (!found) {
                return ""; // pas de tableau vide dans le rapport
            }

        } catch (IOException e) {
            log.error("❌ Erreur lors de la lecture du pom.xml : " + e.getMessage());
        }

        return markdown.toString();
    }
}