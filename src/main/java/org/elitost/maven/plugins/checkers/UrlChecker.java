package org.elitost.maven.plugins.checkers;

import org.apache.maven.plugin.logging.Log;
import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vérifie la présence et la validité de la balise <url> dans le POM.
 */
public class UrlChecker implements CustomChecker, BasicInitializableChecker {

    private static final String CHECKER_ID = "urls";
    private static final String LOG_PREFIX = "[UrlChecker]";
    private static final String URL_PATTERN = "<url>(.*?)</url>";
    private static final String HTTPS_PREFIX = "https://";
    private static final int CONNECTION_TIMEOUT_MS = 5000;

    private Log log;
    private ReportRenderer renderer;

    public UrlChecker() {
        // Constructeur sans argument requis pour SPI
    }

    @Override
    public void init(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    @Override
    public String getId() {
        return CHECKER_ID;
    }

    @Override
    public String generateReport(CheckerContext checkerContext) {
        String artifactId = checkerContext.getCurrentModule().getArtifactId();
        Optional<UrlCheckResult> checkResult = checkUrlValidity(checkerContext.getCurrentModule().getFile(), artifactId);

        return checkResult
                .filter(UrlCheckResult::hasIssue)
                .map(this::buildReport)
                .orElse("");
    }

    private Optional<UrlCheckResult> checkUrlValidity(File pomFile, String artifactId) {
        try {
            Optional<String> urlOptional = extractUrlFromPom(pomFile);

            if (urlOptional.isEmpty()) {
                return Optional.of(new UrlCheckResult(
                        "🔗 Problème avec la balise <url> dans `" + artifactId + "`",
                        "Aucune balise `<url>` trouvée dans le `pom.xml`.",
                        true
                ));
            }

            String url = urlOptional.get();
            if (!url.startsWith(HTTPS_PREFIX)) {
                log.warn(LOG_PREFIX + " L'URL n'est pas sécurisée : " + url);
                return Optional.of(new UrlCheckResult(
                        "🔗 Problème d'URL non sécurisée dans `" + artifactId + "`",
                        "L'URL doit commencer par `https://` : " + url,
                        true
                ));
            }

            if (!isUrlAccessible(url)) {
                return Optional.of(new UrlCheckResult(
                        "🔗 L'URL ne répond pas dans `" + artifactId + "`",
                        "L'URL ne répond pas correctement : " + url,
                        true
                ));
            }

            return Optional.empty();
        } catch (Exception e) {
            log.error(LOG_PREFIX + " Erreur lors de la vérification de l'URL", e);
            return Optional.of(new UrlCheckResult(
                    "🔗 Erreur d'analyse de l'URL dans `" + artifactId + "`",
                    "Erreur lors de la vérification de la balise `<url>` : " + e.getMessage(),
                    true
            ));
        }
    }

    private Optional<String> extractUrlFromPom(File pomFile) throws IOException {
        Pattern pattern = Pattern.compile(URL_PATTERN);
        String content = Files.readString(pomFile.toPath());
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            return Optional.of(matcher.group(1).trim());
        }
        return Optional.empty();
    }

    private boolean isUrlAccessible(String urlString) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            connection.setReadTimeout(CONNECTION_TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            log.warn(LOG_PREFIX + " Erreur de connexion à l'URL: " + urlString, e);
            return false;
        }
    }

    private String buildReport(UrlCheckResult result) {
        return renderer.renderHeader3(result.getTitle()) +
                renderer.openIndentedSection() +
                renderer.renderError(result.getMessage()) +
                renderer.closeIndentedSection();
    }

    private static class UrlCheckResult {
        private final String title;
        private final String message;
        private final boolean hasIssue;

        UrlCheckResult(String title, String message, boolean hasIssue) {
            this.title = title;
            this.message = message;
            this.hasIssue = hasIssue;
        }

        String getTitle() {
            return title;
        }

        String getMessage() {
            return message;
        }

        boolean hasIssue() {
            return hasIssue;
        }
    }
}