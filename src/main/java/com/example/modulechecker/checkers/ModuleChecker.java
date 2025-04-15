package com.example.modulechecker.checkers;

import com.example.modulechecker.renderers.ReportRenderer;
import org.apache.maven.project.MavenProject;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Vérifie la présence et la déclaration des modules attendus dans un projet Maven multi-modules.
 * Cette classe génère un rapport via un renderer, indiquant les modules manquants ou non correctement déclarés.
 *
 * <p>Les modules attendus suivent un schéma de nommage basé sur le {@code artifactId} du projet parent,
 * suffixé par des suffixes standards (par défaut : "api", "impl", "local").</p>
 */
public class ModuleChecker {

    private final Log log;
    private final ReportRenderer renderer;

    /**
     * Construit un vérificateur de modules avec un logger Maven et un renderer.
     *
     * @param log le logger Maven à utiliser pour l'affichage des messages de vérification
     * @param renderer le renderer qui sera utilisé pour générer le rapport
     */
    public ModuleChecker(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    /**
     * Génère un rapport via le renderer, listant les modules attendus absents ou non déclarés dans le {@code pom.xml} parent.
     *
     * @param project le projet Maven parent
     * @return une chaîne contenant un rapport généré via le renderer
     */
    public String generateModuleCheckReport(MavenProject project) {
        StringBuilder report = new StringBuilder();
        report.append(renderer.renderTitle("🧩 Vérification des modules du projet `" + project.getArtifactId() + "`"));

        try {
            List<String> expectedModules = getExpectedModules();
            List<String> missingModules = checkMissingModules(project, expectedModules);

            if (missingModules.isEmpty()) {
                report.append(renderer.renderParagraph("✅ Tous les modules attendus sont présents et déclarés."));
                log.info("✅ Tous les modules attendus sont présents pour le projet : " + project.getArtifactId());
            } else {
                Collections.sort(missingModules);

                report.append(renderer.renderParagraph("❌ Certains modules attendus sont absents ou non déclarés :"));
                String[] headers = { "📦 Module manquant" };
                String[][] rows = new String[missingModules.size()][1];
                for (int i = 0; i < missingModules.size(); i++) {
                    rows[i][0] = "❌ " + missingModules.get(i);
                    log.warn("Module manquant ou non déclaré : " + missingModules.get(i));
                }

                report.append(renderer.renderTable(headers, rows));
                report.append(renderer.renderWarning(
                        "Vérifie que chaque module attendu est bien présent dans le filesystem et déclaré dans le &lt;modules&gt; du `pom.xml` parent."
                ));
            }

        } catch (Exception e) {
            report.append(renderer.renderError("Une erreur est survenue lors de la vérification des modules : `" + e.getMessage() + "`"));
            log.error("Erreur pendant la vérification des modules du projet " + project.getArtifactId(), e);
        }

        return report.toString();
    }

    /**
     * Retourne la liste des suffixes de modules attendus pour un projet.
     * <p>Peut être surchargé ou rendu configurable pour refléter la convention d'un projet ou d'une entreprise.</p>
     *
     * @return une liste de suffixes de modules (par exemple : {@code ["api", "impl", "local"]})
     */
    private List<String> getExpectedModules() {
        return List.of("api", "impl", "local");
    }

    /**
     * Vérifie quels modules attendus sont absents du projet.
     * Un module est considéré manquant s'il n'est pas :
     * <ul>
     *     <li>présent dans le système de fichiers (répertoire)</li>
     *     <li>déclaré dans la section {@code <modules>} du {@code pom.xml}</li>
     * </ul>
     *
     * @param project          le projet Maven parent
     * @param expectedModules  la liste complète des modules attendus
     * @return une liste des noms de modules manquants
     */
    private List<String> checkMissingModules(MavenProject project, List<String> expectedModules) {
        List<String> missingModules = new ArrayList<>();

        for (String suffix : expectedModules) {
            String moduleName = project.getArtifactId() + "-" + suffix;
            boolean declared = project.getModules().contains(moduleName);
            boolean exists = new File(project.getBasedir(), moduleName).exists();

            if (!declared || !exists) {
                missingModules.add(moduleName);
            }
        }

        return missingModules;
    }
}