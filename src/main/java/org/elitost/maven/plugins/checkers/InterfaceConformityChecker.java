package org.elitost.maven.plugins.checkers;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Vérifie que toutes les interfaces Java déclarées dans le module <code>-api</code>
 * sont bien référencées dans des tests via des appels à
 * <code>ClassInspector.logClassName(Foo.class)</code>.
 *
 * <p>
 * Ce checker vise à garantir que chaque interface exposée par l'API dispose d'une
 * validation explicite dans les tests unitaires ou d'intégration.
 * Cela permet notamment :
 * </p>
 * <ul>
 *   <li>De s'assurer de la visibilité et du suivi des interfaces exposées</li>
 *   <li>De faciliter les outils d'analyse statique ou de génération de documentation</li>
 *   <li>De repérer les interfaces oubliées ou orphelines</li>
 * </ul>
 *
 * <p>
 * Le rapport généré inclut un tableau listant les interfaces non couvertes par les appels à
 * <code>ClassInspector.logClassName(...)</code>.
 * </p>
 *
 * @author Eric
 */
public class InterfaceConformityChecker implements CustomChecker, BasicInitializableChecker {

    private Log log;
    private ReportRenderer renderer;

    public InterfaceConformityChecker() {
        // Constructeur requis pour le chargement SPI
    }

    @Override
    public void init(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    @Override
    public String getId() {
        return "interfaceConformity";
    }

    /**
     * Génère un rapport listant les interfaces Java non référencées dans des appels
     * à <code>ClassInspector.logClassName(...)</code> dans les tests.
     *
     * @param checkerContext  Projet Maven contenant les interfaces (généralement le module <code>-api</code>)
     * @return Chaîne représentant le rapport complet formaté via le {@link ReportRenderer}
     */
    @Override
    public String generateReport(CheckerContext checkerContext) {
        String artifactId = checkerContext.getCurrentModule().getArtifactId();

        List<String> interfaceNames = collectInterfaceNames(new File(checkerContext.getCurrentModule().getBasedir(), "src/main/java"));
        if (interfaceNames.isEmpty()) {
            log.info("[InterfaceConformityChecker] Aucune interface détectée dans " + artifactId);
            return ""; // ✅ Rien à signaler
        }

        Set<String> loggedInterfaces = findLoggedInterfaces(checkerContext.getRootProject());

        List<String[]> uncovered = interfaceNames.stream()
                .filter(iface -> !loggedInterfaces.contains(iface))
                .map(name -> new String[]{name})
                .collect(Collectors.toList());

        if (uncovered.isEmpty()) {
            log.info("[InterfaceConformityChecker] Toutes les interfaces de " + artifactId + " sont couvertes.");
            return ""; // ✅ Tout est OK → pas de rapport
        }

        StringBuilder report = new StringBuilder();
        report.append(renderer.renderHeader3("🧪 Conformité des interfaces de `" + artifactId + "`"));
        report.append(renderer.openIndentedSection());
        report.append(renderer.renderWarning("Interfaces non testées par `ClassInspector.logClassName(...)` :"));
        report.append(renderer.renderTable(new String[]{"Interface non testée"}, uncovered.toArray(new String[0][])));
        report.append(renderer.closeIndentedSection());

        return report.toString();
    }

    /**
     * Scanne les fichiers sources du module pour extraire les noms des interfaces Java.
     *
     * @param sourceDir Répertoire racine des sources (src/main/java)
     * @return Liste des noms simples des interfaces trouvées (sans package)
     */
    private List<String> collectInterfaceNames(File sourceDir) {
        if (!sourceDir.exists()) return Collections.emptyList();

        try {
            return Files.walk(sourceDir.toPath())
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(this::extractInterfaceName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Erreur lors du scan des interfaces dans : " + sourceDir, e);
            return Collections.emptyList();
        }
    }

    /**
     * Extrait le nom simple d’une interface à partir d’un fichier .java.
     *
     * @param file Fichier source Java
     * @return Nom de l’interface (ex : "FooApi"), ou null si aucune interface trouvée
     */
    private String extractInterfaceName(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                line = line.trim();
                if (line.contains("interface ")) {
                    String[] tokens = line.split("\\s+");
                    for (int i = 0; i < tokens.length - 1; i++) {
                        if ("interface".equals(tokens[i])) {
                            return tokens[i + 1].replaceAll("[<{].*", ""); // supprime les génériques
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("⚠️ Lecture impossible : " + file);
        }
        return null;
    }

    /**
     * Recherche dans tous les modules Maven les appels à {@code ClassInspector.logClassName(...)} dans les tests.
     *
     * @param rootProject Projet parent contenant tous les modules
     * @return Ensemble des noms d’interfaces référencés dans les tests
     */
    private Set<String> findLoggedInterfaces(MavenProject rootProject) {
        Set<String> found = new HashSet<>();

        List<MavenProject> modules = rootProject.getCollectedProjects();
        if (modules == null) return found;

        for (MavenProject module : modules) {
            File testDir = new File(module.getBasedir(), "src/test/java");
            if (!testDir.exists()) continue;

            try {
                Files.walk(testDir.toPath())
                        .filter(p -> p.toString().endsWith(".java"))
                        .forEach(file -> {
                            try {
                                String content = Files.readString(file);
                                found.addAll(extractLogClassNameCalls(content));
                            } catch (IOException e) {
                                log.warn("⚠️ Lecture échouée pour le fichier : " + file);
                            }
                        });
            } catch (IOException e) {
                log.warn("Erreur lors du parcours de " + module.getArtifactId(), e);
            }
        }

        return found;
    }

    /**
     * Recherche les appels à {@code ClassInspector.logClassName(Foo.class)} dans le contenu d’un fichier Java.
     *
     * @param content contenu brut du fichier source
     * @return Liste des noms d’interfaces passés à la méthode logClassName(...)
     */
    private List<String> extractLogClassNameCalls(String content) {
        List<String> results = new ArrayList<>();
        Pattern pattern = Pattern.compile("ClassInspector\\.logClassName\\(([^)]+)\\.class\\)");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            results.add(matcher.group(1).trim());
        }
        return results;
    }
}