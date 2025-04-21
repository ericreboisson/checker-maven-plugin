package org.elitost.maven.plugins.checkers;

import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vérifie la présence de la balise <url> dans le fichier pom.xml et si l'URL est en HTTPS et répond correctement.
 * Ne génère de rapport que si des problèmes sont détectés.
 */
public class UrlChecker implements CustomChecker {

    private final Log log;
    private final ReportRenderer renderer;

    public UrlChecker(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    @Override
    public String getId() {
        return "";
    }

    /**
     * Génère un rapport **uniquement s’il y a un problème** lié à la balise <url>.
     *
     * @param checkerContext le projet Maven
     * @return un rapport d'erreur ou une chaîne vide si tout est conforme
     */
    @Override
    public String generateReport(CheckerContext checkerContext) {

        String artifactId = checkerContext.getCurrentModule().getArtifactId();
        StringBuilder report = new StringBuilder();
        boolean hasIssue = false;

        try {
            String url = extractUrlFromPom(checkerContext.getCurrentModule().getFile());

            if (url == null || url.isBlank()) {
                hasIssue = true;
                report.append(renderer.renderHeader3("🔗 Problème avec la balise <url> dans `" + artifactId + "`"));
                report.append(renderer.openIndentedSection());
                report.append(renderer.renderError("Aucune balise `<url>` trouvée dans le `pom.xml`."));
            } else if (!url.startsWith("https://")) {
                hasIssue = true;
                report.append(renderer.renderHeader3("🔗 Problème d'URL non sécurisée dans `" + artifactId + "`"));
                report.append(renderer.openIndentedSection());
                report.append(renderer.renderError("L'URL doit commencer par `https://` : " + url));
                log.warn("[UrlChecker] L'URL n'est pas sécurisée : " + url);
            } else if (!isUrlResponding(url)) {
                hasIssue = true;
                report.append(renderer.renderHeader3("🔗 L'URL ne répond pas dans `" + artifactId + "`"));
                report.append(renderer.openIndentedSection());
                report.append(renderer.renderError("L'URL ne répond pas correctement : " + url));
            }

        } catch (Exception e) {
            hasIssue = true;
            report.append(renderer.renderHeader3("🔗 Erreur d’analyse de l’URL dans `" + artifactId + "`"));
            report.append(renderer.openIndentedSection());
            String errorMessage = "Erreur lors de la vérification de la balise `<url>` : `" + e.getMessage() + "`";
            report.append(renderer.renderError(errorMessage));
            log.error("[UrlChecker] " + errorMessage, e);
        }

        if (hasIssue) {
            report.append(renderer.closeIndentedSection());
            return report.toString();
        }

        return "";
    }

    private String extractUrlFromPom(File pomFile) {
        try {
            Pattern pattern = Pattern.compile("<url>(.*?)</url>");
            String content = new String(Files.readAllBytes(pomFile.toPath()));
            Matcher matcher = pattern.matcher(content);

            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'extraction de l'URL du fichier pom.xml", e);
        }
        return null;
    }

    private boolean isUrlResponding(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.connect();

            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            log.error("Erreur lors de la connexion à l'URL : " + urlString, e);
            return false;
        }
    }

}