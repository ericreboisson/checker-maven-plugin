package com.example.modulechecker.checkers;

import com.example.modulechecker.renderers.ReportRenderer;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommentedTagsChecker {

    private final Log log;
    private final ReportRenderer renderer;

    public CommentedTagsChecker(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    public String generateCommentedTagsReport(MavenProject project) {
        StringBuilder report = new StringBuilder();

        File pomFile = new File(project.getBasedir(), "pom.xml");
        if (!pomFile.exists()) {
            String errorMsg = "❌ Impossible de trouver le fichier pom.xml de " + project.getArtifactId();
            log.warn("[CommentedTagsChecker] " + errorMsg);
            return renderer.renderError(errorMsg);
        }

        try {
            String content = Files.readString(pomFile.toPath());

            Pattern commentPattern = Pattern.compile("<!--(.*?)-->", Pattern.DOTALL);
            Matcher matcher = commentPattern.matcher(content);

            boolean found = false;
            report.append(renderer.renderTitle("🪧 Balises XML commentées détectées dans `pom.xml`"));
            report.append(renderer.renderParagraph(
                    "Ces balises sont **actuellement désactivées** dans le `pom.xml`. " +
                    "Cela peut entraîner des comportements inattendus si elles étaient censées être actives."
            ));

            List<String[]> rows = new ArrayList<>();

            while (matcher.find()) {
                String comment = matcher.group(1).trim();

                if (comment.matches("(?s).*<\\s*(modelVersion|parent|groupId|artifactId|version|packaging|name|description|url|inceptionYear|licenses|license|organization|developers|developer|scm|issueManagement|ciManagement|distributionManagement|repositories|repository|pluginRepositories|pluginRepository|modules|dependencies|dependency|dependencyManagement|build|plugins|plugin|pluginManagement|executions|execution|goals|resources|resource|testResources|testResource|reporting|reports|report|profiles|profile|properties)[^>]*>.*")) {
                    found = true;

                    String escaped = comment
                            .replace("|", "\\|")
                            .replace("<", "&lt;")
                            .replace(">", "&gt;")
                            .replace("\r", "")
                            .replace("\n", "<br/>");

                    rows.add(new String[]{
                            "<details><summary>Afficher le bloc</summary><pre>" + escaped + "</pre></details>"
                    });
                }
            }

            if (found) {
                report.append(renderer.renderTable(
                        new String[]{"Bloc XML commenté"},
                        rows.toArray(new String[0][])
                ));
            } else {
                return ""; // pas de bloc significatif, pas de rendu
            }

        } catch (IOException e) {
            String errorMsg = "❌ Erreur lors de la lecture du pom.xml : " + e.getMessage();
            log.error("[CommentedTagsChecker] " + errorMsg);
            return renderer.renderError(errorMsg);
        }

        return report.toString();
    }
}