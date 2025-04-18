package org.elitost.maven.plugins.checkers;

import org.elitost.maven.plugins.renderers.ReportRenderer;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vérifie la présence de la balise <url> dans le fichier pom.xml et si l'URL est en HTTPS et répond correctement.
 */
public class UrlChecker {

    private final Log log;
    private final ReportRenderer renderer;

    /**
     * Constructeur principal.
     *
     * @param log      Logger Maven
     * @param renderer Renderer de rapport (Markdown, HTML, etc.)
     */
    public UrlChecker(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    /**
     * Génère un rapport sur la vérification de la balise <url> dans le pom.xml.
     *
     * @param project le projet Maven
     * @return un rapport de vérification au format du renderer fourni
     */
    public String generateUrlCheckReport(MavenProject project) {
        String artifactId = project.getArtifactId();
        StringBuilder report = new StringBuilder();

        report.append(renderer.renderHeader3("🔗 Vérification de la balise <url> pour le projet `" + artifactId + "`"));

        try {
            String url = extractUrlFromPom(project.getFile());

            if (url != null && !url.isEmpty()) {
                report.append(renderer.renderParagraph("✅ La balise <url> est présente dans le `pom.xml` : " + url));

                if (!url.startsWith("https://")) {
                    report.append(renderer.renderError("L'URL doit commencer par `https://` : " + url));
                    log.warn("[UrlChecker] L'URL doit commencer par https:// : " + url);
                } else {
                    if (isUrlResponding(url)) {
                        report.append(renderer.renderParagraph("✅ L'URL répond correctement : " + url));
                    } else {
                        report.append(renderer.renderError("L'URL ne répond pas correctement : " + url));
                    }
                }
            } else {
                report.append(renderer.renderError("Pas de balise <url> présente dans le `pom.xml`"));
            }
        } catch (Exception e) {
            String errorMessage = "Une erreur est survenue lors de la vérification de la balise <url> : `" + e.getMessage() + "`";
            report.append(renderer.renderError(errorMessage));
            log.error("[UrlChecker] " + errorMessage, e);
        }

        return report.toString();
    }

    /**
     * Extrait l'URL de la balise <url> dans le fichier pom.xml.
     *
     * @param pomFile Le fichier pom.xml
     * @return l'URL extraite ou null si non trouvée
     */
    private String extractUrlFromPom(File pomFile) {
        try {
            Pattern pattern = Pattern.compile("<url>(.*?)</url>");
            String content = new String(java.nio.file.Files.readAllBytes(pomFile.toPath()));
            Matcher matcher = pattern.matcher(content);

            if (matcher.find()) {
                return matcher.group(1); // Retourne l'URL extraite
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'extraction de l'URL du fichier pom.xml", e);
        }
        return null;
    }

    /**
     * Vérifie si l'URL répond correctement (HTTP 200).
     *
     * @param urlString L'URL à vérifier
     * @return true si l'URL répond correctement, sinon false
     */
    private boolean isUrlResponding(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.connect();

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            log.error("Erreur lors de la connexion à l'URL: " + urlString, e);
            return false;
        }
    }
}