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
import java.util.stream.Collectors;

/**
 * Checker Maven qui détecte les balises XML significatives commentées dans un fichier pom.xml.
 * Cela inclut par exemple les balises {@code <dependencies>}, {@code <build>}, {@code <plugins>}, etc.
 * <p>
 * Les commentaires contenant des balises importantes peuvent indiquer une configuration
 * qui a été temporairement désactivée ou des tests de configuration, ce qui peut
 * conduire à des comportements inattendus lors de la construction.
 */
public class CommentedTagsChecker implements CustomChecker, InitializableChecker {

    /**
     * Liste par défaut des balises Maven significatives à surveiller
     */
    private static final Set<String> DEFAULT_TAG_WHITELIST = new HashSet<>(Arrays.asList(
            // Tags de base du POM
            "modelVersion", "parent", "groupId", "artifactId", "version", "packaging", "name", "description", "url",
            "inceptionYear",
            // Informations sur l'organisation et les contributeurs
            "licenses", "license", "organization", "developers", "developer",
            // Gestion et infrastructure
            "scm", "issueManagement", "ciManagement", "distributionManagement",
            // Référentiels
            "repositories", "repository", "pluginRepositories", "pluginRepository",
            // Structure du projet
            "modules",
            // Dépendances
            "dependencies", "dependency", "dependencyManagement",
            // Construction
            "build", "plugins", "plugin", "pluginManagement", "executions", "execution", "goals",
            // Ressources
            "resources", "resource", "testResources", "testResource",
            // Rapports
            "reporting", "reports", "report",
            // Profils et propriétés
            "profiles", "profile", "properties"
    ));

    /**
     * Motif pour extraire les commentaires XML
     */
    private static final Pattern COMMENT_PATTERN = Pattern.compile("<!--(.*?)-->", Pattern.DOTALL);

    /**
     * Motif pour détecter les balises XML
     */
    private static final Pattern XML_TAG_PATTERN = Pattern.compile("<\\s*([\\w\\-:]+)[^>]*>", Pattern.DOTALL);

    private Log log;
    private ReportRenderer renderer;
    private final Set<String> tagWhitelist;
    private int maxCommentLength = 500;
    private boolean truncateComments = true;

    /**
     * Constructeur sans argument requis pour SPI
     */
    public CommentedTagsChecker() {
        this.tagWhitelist = new HashSet<>(DEFAULT_TAG_WHITELIST);
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
            List<CommentedBlock> commentedBlocks = extractCommentedXmlBlocks(content);

            if (commentedBlocks.isEmpty()) return "";

            return buildReport(commentedBlocks, checkerContext);

        } catch (IOException e) {
            String errorMsg = "Erreur lors de la lecture du pom.xml : " + e.getMessage();
            log.error("[CommentedTagsChecker] " + errorMsg, e);
            return renderer.renderError(errorMsg);
        }
    }

    /**
     * Construit le rapport des blocs commentés
     */
    private String buildReport(List<CommentedBlock> commentedBlocks, CheckerContext checkerContext) {
        StringBuilder report = new StringBuilder();
        report.append(renderer.renderHeader3("🪧 Balises XML commentées détectées dans `pom.xml`"));
        report.append(renderer.openIndentedSection());
        report.append(renderer.renderWarning(
                "Les balises ci-dessous sont désactivées via des commentaires. " +
                        "Cela peut provoquer des comportements inattendus ou des configurations manquantes."));

        List<String[]> rows = new ArrayList<>();
        for (CommentedBlock block : commentedBlocks) {

            rows.add(new String[] {
                    formatCommentAsHtml(block.content)
            });
        }

        report.append(renderer.renderTable(new String[]{"Bloc XML commenté"}, rows.toArray(new String[0][])));

        // Ajouter des suggestions pour résoudre le problème en utilisant seulement renderParagraph
        report.append(renderer.renderHeader3("Suggestions"));
        report.append(renderer.renderParagraph(
                "Pour résoudre ces problèmes :\n" +
                        "• Décommenter les balises si elles sont nécessaires à la configuration\n" +
                        "• Supprimer entièrement les commentaires s'ils ne sont plus pertinents\n" +
                        "• Ajouter un commentaire explicatif si le code commenté est conservé intentionnellement"
        ));

        report.append(renderer.closeIndentedSection());

        return report.toString();
    }

    /**
     * Classe représentant un bloc commenté avec les balises qu'il contient
     */
    private static class CommentedBlock {
        private final String content;
        private final List<String> tags;

        public CommentedBlock(String content, List<String> tags) {
            this.content = content;
            this.tags = tags;
        }
    }

    /**
     * Extrait les blocs XML commentés contenant des balises significatives
     */
    private List<CommentedBlock> extractCommentedXmlBlocks(String content) {
        List<CommentedBlock> results = new ArrayList<>();
        Matcher matcher = COMMENT_PATTERN.matcher(content);

        while (matcher.find()) {
            String comment = matcher.group(1).trim();
            List<String> significantTags = extractSignificantTags(comment);

            if (!significantTags.isEmpty()) {
                results.add(new CommentedBlock(comment, significantTags));
            }
        }
        return results;
    }

    /**
     * Extrait les balises significatives d'un bloc de texte.
     * Utilise une expression régulière pour identifier toutes les balises XML.
     */
    private List<String> extractSignificantTags(String text) {
        List<String> foundTags = new ArrayList<>();
        Matcher tagMatcher = XML_TAG_PATTERN.matcher(text);

        while (tagMatcher.find()) {
            String tagName = tagMatcher.group(1);
            if (tagWhitelist.contains(tagName)) {
                foundTags.add(tagName);
            }
        }

        return foundTags;
    }

    /**
     * Formate un commentaire pour l'affichage HTML
     * Si le commentaire est trop long, il est tronqué pour améliorer la lisibilité
     */
    private String formatCommentAsHtml(String comment) {
        String formattedComment = comment;

        if (truncateComments && comment.length() > maxCommentLength) {
            formattedComment = comment.substring(0, maxCommentLength) + "... [tronqué]";
        }

        return "<details open><summary>Afficher</summary><pre>" +
                escapeHtml(formattedComment) +
                "</pre></details>";
    }

    /**
     * Échappe les caractères spéciaux HTML
     */
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