package org.elitost.maven.plugins.checkers;

import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;
import org.elitost.maven.plugins.utils.HtmlUtils;
import org.elitost.maven.plugins.utils.Symbols;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * Motif pour détecter les propriétés dans les commentaires XML
     */
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("<\\s*([\\w\\-.]+)\\s*>(.*?)<\\s*/\\s*\\1\\s*>", Pattern.DOTALL);

    private final Set<String> tagWhitelist;
    private Log log;
    private ReportRenderer renderer;

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
        if (isInvalidContext(checkerContext)) {
            return handleInvalidContext();
        }

        File pomFile = getPomFile(checkerContext);
        if (!pomFile.exists()) {
            return handleMissingPomFile(checkerContext);
        }

        try {
            String content = readPomFileContent(pomFile);
            if (content.trim().isEmpty()) {
                log.info("[CommentedTagsChecker] Le fichier pom.xml est vide.");
                return "";
            }

            log.debug("[CommentedTagsChecker] Début de l'analyse du fichier pom.xml...");
            List<CommentedBlock> commentedBlocks = extractCommentedXmlBlocks(content);

            if (commentedBlocks.isEmpty()) {
                log.info("[CommentedTagsChecker] Aucun bloc XML commenté significatif détecté.");
                return "";
            }

            log.debug("[CommentedTagsChecker] Nombre de blocs XML commentés détectés : " + commentedBlocks.size());
            return buildReport(commentedBlocks);

        } catch (IOException e) {
            return handleIOException(e);
        } catch (Exception e) {
            return handleUnexpectedException(e);
        }
    }

    private boolean isInvalidContext(CheckerContext checkerContext) {
        return checkerContext == null || checkerContext.getCurrentModule() == null;
    }

    private String handleInvalidContext() {
        String errorMsg = "Le contexte ou le module actuel est null.";
        log.error("[CommentedTagsChecker] " + errorMsg);
        return renderer.renderError(errorMsg);
    }

    private File getPomFile(CheckerContext checkerContext) {
        return new File(checkerContext.getCurrentModule().getBasedir(), "pom.xml");
    }

    private String handleMissingPomFile(CheckerContext checkerContext) {
        String msg = Symbols.ERROR + "Fichier pom.xml introuvable pour le module : " + checkerContext.getCurrentModule().getArtifactId();
        log.warn("[CommentedTagsChecker] " + msg);
        return renderer.renderError(msg);
    }

    private String readPomFileContent(File pomFile) throws IOException {
        return new String(Files.readAllBytes(pomFile.toPath()));
    }

    private String handleIOException(IOException e) {
        String errorMsg = "Erreur lors de la lecture du fichier pom.xml : " + e.getMessage();
        log.error("[CommentedTagsChecker] " + errorMsg, e);
        return renderer.renderError(errorMsg);
    }

    private String handleUnexpectedException(Exception e) {
        String errorMsg = "Erreur inattendue lors de l'analyse du fichier pom.xml : " + e.getMessage();
        log.error("[CommentedTagsChecker] " + errorMsg, e);
        return renderer.renderError(errorMsg);
    }

    private List<CommentedBlock> extractCommentedXmlBlocks(String content) {
        List<CommentedBlock> results = new ArrayList<>();
        Matcher matcher = COMMENT_PATTERN.matcher(content);

        while (matcher.find()) {
            String comment = matcher.group(1).trim();
            if (comment.isEmpty()) continue;

            List<String> significantTags = extractSignificantTags(comment);
            List<String> commentedProperties = extractCommentedProperties(comment);

            if (isSignificantBlock(significantTags, commentedProperties)) {
                results.add(new CommentedBlock(comment));
            }
        }
        return results;
    }

    private boolean isSignificantBlock(List<String> significantTags, List<String> commentedProperties) {
        return !significantTags.isEmpty() || !commentedProperties.isEmpty();
    }

    private String buildReport(List<CommentedBlock> commentedBlocks) {
        StringBuilder report = new StringBuilder();
        appendReportHeader(report);
        appendCommentedBlocksTable(report, commentedBlocks);
        appendSuggestions(report);
        return report.toString();
    }

    private void appendReportHeader(StringBuilder report) {
        report.append(renderer.renderHeader3("🪧 Balises XML commentées détectées dans `pom.xml`"))
                .append(renderer.openIndentedSection())
                .append(renderer.renderWarning(
                        "Les balises ci-dessous sont désactivées via des commentaires. " +
                                "Cela peut provoquer des comportements inattendus ou des configurations manquantes."));
    }

    private void appendCommentedBlocksTable(StringBuilder report, List<CommentedBlock> commentedBlocks) {
        List<String[]> rows = new ArrayList<>();
        for (CommentedBlock block : commentedBlocks) {
            rows.add(formatCommentedBlockRow(block));
        }
        report.append(renderer.renderTable(
                new String[]{"Bloc XML commenté"},
                rows.toArray(new String[0][])
        ));
    }

    private String[] formatCommentedBlockRow(CommentedBlock block) {
        return new String[]{
                formatCommentAsHtml(block.content)
        };
    }

    private void appendSuggestions(StringBuilder report) {
        report.append(renderer.renderHeader3("Suggestions"))
                .append(renderer.renderParagraph(
                        "Pour résoudre ces problèmes :\n" +
                                "• Dé commenter les balises ou propriétés si elles sont nécessaires à la configuration\n" +
                                "• Supprimer entièrement les commentaires s'ils ne sont plus pertinents\n" +
                                "• Ajouter un commentaire explicatif si le code commenté est conservé intentionnellement"
                ))
                .append(renderer.closeIndentedSection());
    }

    /**
     * Extrait les balises significatives d'un bloc de texte.
     * Utilise une expression régulière pour identifier toutes les balises XML.
     */
    private List<String> extractSignificantTags(String text) {
        List<String> tags = new ArrayList<>();
        Matcher matcher = XML_TAG_PATTERN.matcher(text);
        while (matcher.find()) { // Remplace Matcher.results()
            String tag = matcher.group(1);
            if (tagWhitelist.contains(tag) && isValidTagName(tag)) {
                log.debug("[CommentedTagsChecker] Balise significative détectée : " + tag);
                tags.add(tag);
            }
        }
        return tags;
    }

    /**
     * Valide le nom d'une balise XML.
     * Ajout d'une vérification pour éviter les noms de balises null ou vides.
     */
    private boolean isValidTagName(String tagName) {
        if (tagName == null || tagName.isEmpty()) {
            log.warn("[CommentedTagsChecker] Nom de balise null ou vide détecté.");
            return false;
        }
        return tagName.matches("[\\w\\-:]+");
    }

    /**
     * Extrait les propriétés commentées d'un bloc de texte.
     */
    private List<String> extractCommentedProperties(String text) {
        List<String> properties = new ArrayList<>();
        Matcher matcher = PROPERTY_PATTERN.matcher(text);
        while (matcher.find()) { // Remplace Matcher.results()
            properties.add(matcher.group(1) + "=" + matcher.group(2));
        }
        return properties;
    }

    /**
     * Formate un commentaire pour l'affichage HTML
     * Si le commentaire est trop long, il est tronqué pour améliorer la lisibilité
     */
    private String formatCommentAsHtml(String comment) {
        int maxCommentLength = 500;
        String formattedComment = comment.length() > maxCommentLength
                ? comment.substring(0, maxCommentLength) + "... [tronqué]"
                : comment;

        return String.format(
                "<details open><summary>Afficher</summary><pre>%s</pre></details>",
                HtmlUtils.escapeHtml(formattedComment) // Utilisation de la méthode utilitaire
        );
    }

    /**
     * Classe représentant un bloc commenté avec les balises et propriétés qu'il contient
     */
    private static class CommentedBlock {
        private final String content;

        public CommentedBlock(String content) {
            this.content = content;
        }
    }
}

