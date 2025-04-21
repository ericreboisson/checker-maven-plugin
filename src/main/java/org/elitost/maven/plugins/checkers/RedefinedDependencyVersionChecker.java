package org.elitost.maven.plugins.checkers;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;

import java.util.*;

/**
 * Vérifie si un projet Maven redéfinit des versions de dépendances qui sont déjà héritées du {@code <dependencyManagement>}
 * du projet parent.
 *
 * <p>Ce checker identifie les cas où une dépendance est déclarée avec une version différente de celle définie dans le parent,
 * ce qui peut provoquer des conflits, de la duplication ou une incohérence des versions au sein du build.</p>
 *
 * <p>Il est recommandé d’éviter les redéfinitions explicites inutiles, surtout si la version est déjà correctement centralisée.</p>
 *
 * <p>Exemple de dépendance redéfinie :</p>
 * <pre>{@code
 * <dependency>
 *     <groupId>commons-codec</groupId>
 *     <artifactId>commons-codec</artifactId>
 *     <version>1.15</version> <!-- Redéfinie alors que 1.14 est héritée du parent -->
 * </dependency>
 * }</pre>
 *
 * Le rapport généré affiche un tableau comparant la version héritée et la version redéfinie.
 */
public class RedefinedDependencyVersionChecker implements CustomChecker{

    private final Log log;
    private final ReportRenderer renderer;

    /**
     * Constructeur principal du checker.
     *
     * @param log      le logger Maven pour les messages d'information et d'avertissement.
     * @param renderer le renderer utilisé pour générer le rapport (Markdown, HTML, etc.)
     */
    public RedefinedDependencyVersionChecker(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    @Override
    public String getId() {
        return "";
    }



    /**
     * Génère un rapport des dépendances dont les versions redéfinissent celles déclarées dans le {@code <dependencyManagement>} du parent.
     *
     * @param checkerContext le projet Maven à analyser
     * @return le contenu du rapport au format du renderer, ou une chaîne vide si aucun conflit détecté
     */
    @Override
    public String generateReport(CheckerContext checkerContext) {
        StringBuilder report = new StringBuilder();

        try {
            Map<String, String> inheritedVersions = getManagedDependencyVersions(checkerContext.getCurrentModule().getParent());
            List<String[]> redefined = new ArrayList<>();

            for (Dependency dep : checkerContext.getCurrentModule().getDependencies()) {
                String key = dep.getGroupId() + ":" + dep.getArtifactId();
                String declaredVersion = dep.getVersion();
                String inheritedVersion = inheritedVersions.get(key);

                if (declaredVersion != null && inheritedVersion != null && !declaredVersion.equals(inheritedVersion)) {
                    redefined.add(new String[]{key, inheritedVersion, declaredVersion});
                    log.warn("[RedefinedDependencyVersionChecker] 🔁 " + key + " redéfini : " + inheritedVersion + " ➝ " + declaredVersion);
                }
            }

            if (!redefined.isEmpty()) {
                report.append(renderer.renderHeader3("🔁 Dépendances redéfinies dans `" + checkerContext.getCurrentModule().getArtifactId() + "`"));
                report.append(renderer.openIndentedSection());
                report.append(renderer.renderParagraph(
                        "⚠️ Certaines dépendances redéfinissent une version différente de celle héritée :"
                ));
                report.append(renderer.renderTable(
                        new String[]{"Dépendance", "Version héritée", "Version redéfinie"},
                        redefined.toArray(new String[0][0])
                ));
            }

        } catch (Exception e) {
            log.error("[RedefinedDependencyVersionChecker] Exception levée", e);
            report.append(renderer.renderError("❌ Une erreur est survenue : " + e.getMessage()));
        }

        report.append(renderer.closeIndentedSection());
        return report.toString();
    }

    /**
     * Extrait les versions des dépendances gérées dans le {@code <dependencyManagement>} du parent.
     *
     * @param parent le projet Maven parent
     * @return une map {@code groupId:artifactId ➝ version} représentant les dépendances gérées par le parent
     */
    private Map<String, String> getManagedDependencyVersions(MavenProject parent) {
        if (parent == null) return Collections.emptyMap();

        DependencyManagement depMgmt = parent.getDependencyManagement();
        if (depMgmt == null) return Collections.emptyMap();

        Map<String, String> versions = new HashMap<>();
        for (Dependency dep : depMgmt.getDependencies()) {
            if (dep.getVersion() != null) {
                String key = dep.getGroupId() + ":" + dep.getArtifactId();
                versions.put(key, dep.getVersion());
            }
        }
        return versions;
    }

}