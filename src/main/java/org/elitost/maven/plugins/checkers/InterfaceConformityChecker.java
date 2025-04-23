package org.elitost.maven.plugins.checkers;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Vérifie que toutes les interfaces Java déclarées dans le module <code>-api</code>
 * sont bien référencées dans des tests via des appels à
 * <code>ClassInspector.logClassName(Foo.class)</code>.
 *
 * <p>Configuration possible via properties Maven :</p>
 * <ul>
 *   <li><code>interfaceConformity.skip</code> : désactive complètement le checker</li>
 *   <li><code>interfaceConformity.includePattern</code> : pattern regex pour filtrer les interfaces à vérifier</li>
 *   <li><code>interfaceConformity.excludePattern</code> : pattern regex pour exclure des interfaces de la vérification</li>
 *   <li><code>interfaceConformity.inspectorMethod</code> : nom alternatif pour la méthode d'inspection</li>
 * </ul>
 */
public class InterfaceConformityChecker implements CustomChecker, BasicInitializableChecker {

    private static final String DEFAULT_INSPECTOR_METHOD = "ClassInspector.logClassName";
    private static final Pattern LOG_CALL_PATTERN = Pattern.compile("(\\w+)\\.logClassName\\(([^)]+)\\.class\\)");
    private static final Pattern INTERFACE_PATTERN = Pattern.compile("\\binterface\\s+([\\w<]+)");

    private Log log;
    private ReportRenderer renderer;
    private boolean skip;
    private Pattern includePattern;
    private Pattern excludePattern;
    private String inspectorMethod;

    public InterfaceConformityChecker() {
        // Constructeur requis pour le chargement SPI
    }

    @Override
    public void init(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
        // Configuration par défaut
        this.skip = false;
        this.includePattern = null;
        this.excludePattern = null;
        this.inspectorMethod = DEFAULT_INSPECTOR_METHOD;
    }

    @Override
    public String getId() {
        return "interfaceConformity";
    }

    @Override
    public String generateReport(CheckerContext checkerContext) {
        if (skip) {
            log.info("[InterfaceConformityChecker] Checker désactivé par configuration");
            return "";
        }

        MavenProject currentModule = checkerContext.getCurrentModule();
        String artifactId = currentModule.getArtifactId();

        List<String> interfaceNames = collectInterfaceNames(new File(currentModule.getBasedir(), "src/main/java"));
        if (interfaceNames.isEmpty()) {
            log.info("[InterfaceConformityChecker] Aucune interface détectée dans " + artifactId);
            return "";
        }

        Set<String> loggedInterfaces = findLoggedInterfaces(checkerContext.getRootProject());

        List<String[]> uncovered = interfaceNames.stream()
                .filter(this::shouldCheckInterface)
                .filter(iface -> !loggedInterfaces.contains(iface))
                .sorted()
                .map(name -> new String[]{name})
                .collect(Collectors.toList());

        if (uncovered.isEmpty()) {
            log.info("[InterfaceConformityChecker] Toutes les interfaces de " + artifactId + " sont couvertes.");
            return "";
        }

        return generateUncoveredInterfacesReport(artifactId, uncovered);
    }

    private boolean shouldCheckInterface(String interfaceName) {
        if (includePattern != null && !includePattern.matcher(interfaceName).matches()) {
            return false;
        }
        return excludePattern == null || !excludePattern.matcher(interfaceName).matches();
    }

    private String generateUncoveredInterfacesReport(String artifactId, List<String[]> uncovered) {

        return renderer.renderHeader3("🧪 Conformité des interfaces de `" + artifactId + "`") +
                renderer.openIndentedSection() +
                renderer.renderWarning(String.format(
                        "%d interfaces non référencées par `%s(...)` :",
                        uncovered.size(),
                        inspectorMethod)) +
                renderer.renderTable(
                        new String[]{"Interface non testée"},
                        uncovered.toArray(new String[0][])) +
                renderer.renderParagraph("💡 Conseil : ajoutez des appels à " + inspectorMethod +
                        " dans vos tests pour chaque interface exposée.") +
                renderer.closeIndentedSection();
    }

    private List<String> collectInterfaceNames(File sourceDir) {
        if (!sourceDir.exists()) {
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.walk(sourceDir.toPath())) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .parallel()
                    .map(this::extractInterfaceName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Erreur lors du scan des interfaces dans : " + sourceDir, e);
            return Collections.emptyList();
        }
    }

    private String extractInterfaceName(Path file) {
        try {
            String content = Files.readString(file);
            Matcher matcher = INTERFACE_PATTERN.matcher(content);
            if (matcher.find()) {
                return matcher.group(1).replaceAll("[<{].*", "");
            }
        } catch (IOException e) {
            log.warn("⚠️ Lecture impossible : " + file);
        }
        return null;
    }

    private Set<String> findLoggedInterfaces(MavenProject rootProject) {
        Set<String> found = new HashSet<>();
        List<MavenProject> modules = Optional.ofNullable(rootProject.getCollectedProjects())
                .orElse(Collections.emptyList());

        modules.parallelStream().forEach(module -> {
            File testDir = new File(module.getBasedir(), "src/test/java");
            if (!testDir.exists()) return;

            try (Stream<Path> paths = Files.walk(testDir.toPath())) {
                paths.filter(p -> p.toString().endsWith(".java"))
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
        });

        return found;
    }

    private List<String> extractLogClassNameCalls(String content) {
        List<String> results = new ArrayList<>();
        Matcher matcher = LOG_CALL_PATTERN.matcher(content);

        while (matcher.find()) {
            if (matcher.group(1).equals(inspectorMethod.split("\\.")[0])) {
                results.add(matcher.group(2).trim());
            }
        }
        return results;
    }
}