package org.elitost.maven.plugins.checkers;

import org.elitost.maven.plugins.renderers.ReportRenderer;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checker Maven qui détecte les blocs XML significatifs commentés dans un fichier `pom.xml`.
 * Ces balises peuvent masquer des configurations importantes et induire des comportements inattendus.
 *
 * Exemples : <dependencies>, <build>, <plugins>, etc.
 */
public class CommentedTagsChecker {

    private static final Set<String> TAG_WHITELIST = Set.of(
            "modelVersion", "parent", "groupId", "artifactId", "version", "packaging", "name", "description", "url",
            "inceptionYear", "licenses", "license", "organization", "developers", "developer", "scm", "issueManagement",
            "ciManagement", "distributionManagement", "repositories", "repository", "pluginRepositories",
            "pluginRepository", "modules", "dependencies", "dependency", "dependencyManagement", "build", "plugins",
            "plugin", "pluginManagement", "executions", "execution", "goals", "resources", "resource", "testResources",
            "testResource", "reporting", "reports", "report", "profiles", "profile", "properties"
    );

    private static final Pattern COMMENT_PATTERN = Pattern.compile("<!--(.*?)-->", Pattern.DOTALL);

    private final Log log;
    private final ReportRenderer renderer;

    public CommentedTagsChecker(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    /**
     * Génère un rapport listant les blocs XML significatifs commentés dans le pom.xml du projet.
     *
     * @param project Le projet Maven à analyser
     * @return Rapport au format string (Markdown ou HTML selon renderer), ou vide si rien à signaler.
     */
    public String generateCommentedTagsReport(MavenProject project) {
        File pomFile = new File(project.getBasedir(), "pom.xml");
        if (!pomFile.exists()) {
            String errorMsg = "❌ Impossible de trouver le fichier pom.xml de " + project.getArtifactId();
            log.warn("[CommentedTagsChecker] " + errorMsg);
            return renderer.renderError(errorMsg);
        }

        try {
            String content = Files.readString(pomFile.toPath());
            List<String> commentedBlocks = extractCommentedXmlBlocks(content);

            if (commentedBlocks.isEmpty()) {
                return ""; // Aucun bloc pertinent commenté
            }

            StringBuilder report = new StringBuilder();
            report.append(renderer.renderAnchor("commented-tags"));
            report.append(renderer.renderHeader3("🪧 Balises XML commentées détectées dans `pom.xml`"));
            report.append(renderer.renderParagraph(
                    "Les balises ci-dessous sont actuellement désactivées dans le `pom.xml`. " +
                            "Cela peut entraîner des comportements inattendus si elles étaient censées être actives."
            ));

            List<String[]> rows = new ArrayList<>();
            for (String comment : commentedBlocks) {
                rows.add(new String[]{formatCommentAsHtml(comment)});
            }

            report.append(renderer.renderTable(
                    new String[]{"Bloc XML commenté"},
                    rows.toArray(new String[0][])
            ));

            return report.toString();

        } catch (IOException e) {
            String errorMsg = "❌ Erreur lors de la lecture du pom.xml : " + e.getMessage();
            log.error("[CommentedTagsChecker] " + errorMsg);
            return renderer.renderError(errorMsg);
        }
    }

    /**
     * Extrait les blocs XML commentés contenant au moins une balise Maven significative.
     *
     * @param content Le contenu brut du fichier pom.xml
     * @return Liste des blocs commentés pertinents
     */
    private List<String> extractCommentedXmlBlocks(String content) {
        List<String> results = new ArrayList<>();
        Matcher matcher = COMMENT_PATTERN.matcher(content);

        while (matcher.find()) {
            String comment = matcher.group(1).trim();
            if (isXmlTagBlock(comment)) {
                results.add(comment);
            }
        }

        return results;
    }

    /**
     * Vérifie si un commentaire contient une balise Maven significative.
     *
     * @param comment Le contenu du commentaire
     * @return true si le commentaire contient une balise de la whitelist
     */
    private boolean isXmlTagBlock(String comment) {
        for (String tag : TAG_WHITELIST) {
            if (comment.matches("(?s).*<\\s*" + tag + "[^>]*>.*")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Échappe les caractères HTML et rend le bloc prêt à être intégré dans un tableau Markdown/HTML.
     *
     * @param comment Bloc XML brut
     * @return Contenu formaté avec balisage HTML
     */
    private String formatCommentAsHtml(String comment) {
        return "<details><summary>Afficher le bloc</summary><pre>" +
                escapeHtml(comment) +
                "</pre></details>";
    }

    /**
     * Échappe les caractères spéciaux HTML.
     */
    private String escapeHtml(String input) {
        return input.replace("&", "&amp;")
                .replace("|", "\\|")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\r", "")
                .replace("\n", "<br/>");
    }
}