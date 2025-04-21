package org.elitost.maven.plugins.checkers;

import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checker Maven qui détecte les balises XML significatives commentées dans un fichier pom.xml.
 * Cela inclut par exemple les balises <dependencies>, <build>, <plugins>, etc.
 */
public class CommentedTagsChecker implements CustomChecker, InitializableChecker {

    private static final Set<String> TAG_WHITELIST = new HashSet<>(Arrays.asList(
            "modelVersion", "parent", "groupId", "artifactId", "version", "packaging", "name", "description", "url",
            "inceptionYear", "licenses", "license", "organization", "developers", "developer", "scm", "issueManagement",
            "ciManagement", "distributionManagement", "repositories", "repository", "pluginRepositories",
            "pluginRepository", "modules", "dependencies", "dependency", "dependencyManagement", "build", "plugins",
            "plugin", "pluginManagement", "executions", "execution", "goals", "resources", "resource", "testResources",
            "testResource", "reporting", "reports", "report", "profiles", "profile", "properties"
    ));

    private static final Pattern COMMENT_PATTERN = Pattern.compile("<!--(.*?)-->", Pattern.DOTALL);

    private Log log;
    private ReportRenderer renderer;

    public CommentedTagsChecker() {
        // Constructeur sans argument requis pour SPI
    }

    @Override
    public void init(Log log, RepositorySystem repoSystem, RepositorySystemSession session,
                     List<RemoteRepository> remoteRepositories, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    @Override
    public String getId() {
        return "commentedTags";
    }

    @Override
    public String generateReport(CheckerContext checkerContext) {
        File pomFile = new File(checkerContext.getCurrentModule().getBasedir(), "pom.xml");
        if (!pomFile.exists()) {
            String msg = "❌ Impossible de trouver le fichier pom.xml de " + checkerContext.getCurrentModule().getArtifactId();
            log.warn("[CommentedTagsChecker] " + msg);
            return renderer.renderError(msg);
        }

        try {
            String content = Files.readString(pomFile.toPath());
            List<String> commentedBlocks = extractCommentedXmlBlocks(content);

            if (commentedBlocks.isEmpty()) return "";

            StringBuilder report = new StringBuilder();
            report.append(renderer.renderHeader3("🪧 Balises XML commentées détectées dans `pom.xml`"));
            report.append(renderer.openIndentedSection());
            report.append(renderer.renderParagraph(
                    "Les balises ci-dessous sont désactivées via des commentaires. " +
                            "Cela peut provoquer des comportements inattendus."));

            List<String[]> rows = new ArrayList<>();
            for (String comment : commentedBlocks) {
                rows.add(new String[]{ formatCommentAsHtml(comment) });
            }

            report.append(renderer.renderTable(new String[]{"Bloc XML commenté"}, rows.toArray(new String[0][])));
            report.append(renderer.closeIndentedSection());

            return report.toString();

        } catch (IOException e) {
            String errorMsg = "Erreur lors de la lecture du pom.xml : " + e.getMessage();
            log.error("[CommentedTagsChecker] " + errorMsg, e);
            return renderer.renderError(errorMsg);
        }
    }

    private List<String> extractCommentedXmlBlocks(String content) {
        List<String> results = new ArrayList<>();
        Matcher matcher = COMMENT_PATTERN.matcher(content);

        while (matcher.find()) {
            String comment = matcher.group(1).trim();
            if (containsSignificantTag(comment)) {
                results.add(comment);
            }
        }
        return results;
    }

    private boolean containsSignificantTag(String comment) {
        for (String tag : TAG_WHITELIST) {
            if (comment.matches("(?s).*<\\s*" + tag + "[^>]*>.*")) {
                return true;
            }
        }
        return false;
    }

    private String formatCommentAsHtml(String comment) {
        return "<details open><summary>Afficher</summary><pre>" +
                escapeHtml(comment) +
                "</pre></details>";
    }

    private String escapeHtml(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '|': sb.append("\\|"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\n': sb.append("<br/>"); break;
                case '\r': break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}