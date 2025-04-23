package org.elitost.maven.plugins.checkers;

import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Vérifie que les modules attendus (par convention) sont bien présents sur le disque
 * et déclarés dans le {@code pom.xml} parent d'un projet Maven multi-modules.
 *
 * <p>Les modules attendus sont générés dynamiquement à partir de l'identifiant
 * du projet parent suivi d'un suffixe standard tel que {@code -api}, {@code -impl}, etc.</p>
 *
 * <p>Ce checker peut être configuré via les propriétés Maven suivantes :</p>
 * <ul>
 *   <li>{@code elitost.expectedModules.suffixes} : Liste des suffixes de modules attendus séparés par des virgules</li>
 *   <li>{@code elitost.expectedModules.excludes} : Liste des modules à exclure de la vérification</li>
 *   <li>{@code elitost.expectedModules.optional} : Liste des modules optionnels qui ne génèreront pas d'erreur s'ils sont absents</li>
 * </ul>
 */
public class ExpectedModulesChecker implements CustomChecker, BasicInitializableChecker {

    // Clés des propriétés de configuration
    private static final String PROP_SUFFIXES = "elitost.expectedModules.suffixes";
    private static final String PROP_EXCLUDES = "elitost.expectedModules.excludes";
    private static final String PROP_OPTIONAL = "elitost.expectedModules.optional";

    // Suffixes de modules par défaut
    private static final List<String> DEFAULT_MODULE_SUFFIXES = Arrays.asList(
            "-api",
            "-impl",
            "-local"
    );

    private Log log;
    private ReportRenderer renderer;

    /** Constructeur requis pour le chargement SPI */
    public ExpectedModulesChecker() {
    }

    @Override
    public void init(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    @Override
    public String getId() {
        return "expectedModules";
    }

    @Override
    public String generateReport(CheckerContext checkerContext) {
        MavenProject project = checkerContext.getCurrentModule();
        String artifactId = project.getArtifactId();
        StringBuilder report = new StringBuilder();

        report.append(renderer.renderHeader3("🧩 Vérification des modules du projet `" + artifactId + "`"));
        report.append(renderer.openIndentedSection());

        try {
            // Récupère les propriétés de configuration du projet
            Properties properties = project.getProperties();

            // Détermine les suffixes de modules attendus
            List<String> moduleSuffixes = getConfiguredSuffixes(properties);

            // Récupère les modules à exclure de la vérification
            Set<String> excludedModules = getConfiguredExcludes(properties, artifactId);

            // Récupère les modules optionnels
            Set<String> optionalModules = getConfiguredOptionalModules(properties, artifactId);

            // Génère la liste des modules attendus en fonction des suffixes
            List<String> expectedModules = getExpectedModules(artifactId, moduleSuffixes, excludedModules);

            // Identifie les différents types de problèmes de modules
            ModuleAnalysisResult analysisResult = analyzeModules(project, expectedModules, optionalModules);

            if (analysisResult.hasIssues()) {
                // Génère un rapport détaillé des problèmes
                generateIssuesReport(report, analysisResult);
            } else {
                String successMessage = "✅ Tous les modules attendus sont présents et correctement déclarés.";
                report.append(renderer.renderParagraph(successMessage));
                log.info("[ModuleChecker] " + successMessage);
            }

            // Affiche les modules déclarés mais non attendus par convention
            detectExtraModules(report, project, expectedModules, excludedModules);

        } catch (Exception e) {
            String errorMessage = "Une erreur est survenue lors de la vérification des modules : `" + e.getMessage() + "`";
            report.append(renderer.renderError(errorMessage));
            log.error("[ModuleChecker] " + errorMessage, e);
        }

        report.append(renderer.closeIndentedSection());
        return report.toString();
    }

    /**
     * Récupère les suffixes de modules configurés ou utilise les valeurs par défaut
     */
    private List<String> getConfiguredSuffixes(Properties properties) {
        String suffixesProperty = properties.getProperty(PROP_SUFFIXES);
        if (suffixesProperty != null && !suffixesProperty.trim().isEmpty()) {
            return Arrays.stream(suffixesProperty.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return DEFAULT_MODULE_SUFFIXES;
    }

    /**
     * Récupère les modules à exclure configurés
     */
    private Set<String> getConfiguredExcludes(Properties properties, String baseArtifactId) {
        String excludesProperty = properties.getProperty(PROP_EXCLUDES);
        Set<String> excludes = new HashSet<>();
        if (excludesProperty != null && !excludesProperty.trim().isEmpty()) {
            Arrays.stream(excludesProperty.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(suffix -> suffix.startsWith(baseArtifactId) ? suffix : baseArtifactId + suffix)
                    .forEach(excludes::add);
        }
        return excludes;
    }

    /**
     * Récupère les modules optionnels configurés
     */
    private Set<String> getConfiguredOptionalModules(Properties properties, String baseArtifactId) {
        String optionalProperty = properties.getProperty(PROP_OPTIONAL);
        Set<String> optionalModules = new HashSet<>();
        if (optionalProperty != null && !optionalProperty.trim().isEmpty()) {
            Arrays.stream(optionalProperty.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(suffix -> suffix.startsWith(baseArtifactId) ? suffix : baseArtifactId + suffix)
                    .forEach(optionalModules::add);
        }
        return optionalModules;
    }

    /**
     * Génère la liste des modules attendus en fonction des suffixes et des exclusions
     */
    private List<String> getExpectedModules(String baseArtifactId, List<String> suffixes, Set<String> excludes) {
        return suffixes.stream()
                .map(suffix -> baseArtifactId + suffix)
                .filter(moduleName -> !excludes.contains(moduleName))
                .collect(Collectors.toList());
    }

    /**
     * Analyse détaillée des modules pour identifier différents types de problèmes
     */
    private ModuleAnalysisResult analyzeModules(MavenProject project, List<String> expectedModules, Set<String> optionalModules) {
        ModuleAnalysisResult result = new ModuleAnalysisResult();
        Set<String> declaredModules = new HashSet<>(project.getModules());

        for (String module : expectedModules) {
            boolean isDeclared = declaredModules.contains(module);
            boolean existsOnDisk = new File(project.getBasedir(), module).exists();
            boolean hasPomFile = new File(project.getBasedir(), module + "/pom.xml").exists();
            boolean isOptional = optionalModules.contains(module);

            if (!isDeclared && !existsOnDisk) {
                if (!isOptional) {
                    result.modulesCompletelyMissing.add(module);
                }
            } else if (!isDeclared) {
                result.modulesExistButNotDeclared.add(module);
            } else if (!existsOnDisk) {
                result.modulesDeclaredButMissing.add(module);
            } else if (!hasPomFile) {
                result.modulesExistButNoPom.add(module);
            }
        }

        return result;
    }

    /**
     * Génère un rapport détaillé des problèmes détectés
     */
    private void generateIssuesReport(StringBuilder report, ModuleAnalysisResult analysisResult) {
        if (!analysisResult.modulesCompletelyMissing.isEmpty()) {
            report.append(renderer.renderError("Modules attendus manquants (non déclarés et absents du disque) :"));
            renderModuleList(report, analysisResult.modulesCompletelyMissing, "Module manquant");
            report.append(renderer.renderParagraph(
                    "💡 Ces modules sont attendus par convention. Tu dois les créer et les déclarer dans le pom.xml parent."
            ));
        }

        if (!analysisResult.modulesExistButNotDeclared.isEmpty()) {
            report.append(renderer.renderError("Modules existants mais non déclarés dans le pom.xml :"));
            renderModuleList(report, analysisResult.modulesExistButNotDeclared, "Module non déclaré");
            report.append(renderer.renderParagraph(
                    "💡 Ces modules existent sur le disque mais ne sont pas déclarés dans la section `<modules>` du pom.xml parent."
            ));
        }

        if (!analysisResult.modulesDeclaredButMissing.isEmpty()) {
            report.append(renderer.renderError("Modules déclarés mais absents du disque :"));
            renderModuleList(report, analysisResult.modulesDeclaredButMissing, "Module absent");
            report.append(renderer.renderParagraph(
                    "💡 Ces modules sont déclarés dans le pom.xml mais n'existent pas sur le disque."
            ));
        }

        if (!analysisResult.modulesExistButNoPom.isEmpty()) {
            report.append(renderer.renderError("Modules existants mais sans fichier pom.xml :"));
            renderModuleList(report, analysisResult.modulesExistButNoPom, "Module sans pom");
            report.append(renderer.renderParagraph(
                    "💡 Ces modules existent sur le disque mais ne contiennent pas de fichier pom.xml."
            ));
        }
    }

    /**
     * Affiche une liste de modules dans un tableau
     */
    private void renderModuleList(StringBuilder report, List<String> modules, String columnHeader) {
        Collections.sort(modules);
        String[][] rows = modules.stream()
                .map(module -> {
                    log.warn("[ModuleChecker] " + columnHeader + " : " + module);
                    return new String[]{ module };
                })
                .toArray(String[][]::new);

        report.append(renderer.renderTable(new String[]{"📦 " + columnHeader}, rows));
    }

    /**
     * Détecte et signale les modules déclarés mais non attendus par convention
     */
    private void detectExtraModules(StringBuilder report, MavenProject project, List<String> expectedModules, Set<String> excludedModules) {
        Set<String> declaredModules = new HashSet<>(project.getModules());
        List<String> extraModules = declaredModules.stream()
                .filter(module -> !expectedModules.contains(module) && !excludedModules.contains(module))
                .sorted()
                .collect(Collectors.toList());

        if (!extraModules.isEmpty()) {
            report.append(renderer.renderHeader3("ℹ️ Modules supplémentaires"));
            report.append(renderer.renderParagraph(
                    "Ces modules sont déclarés mais ne suivent pas la convention de nommage standard. "
                            + "Ce n'est pas une erreur, juste une information."
            ));

            String[][] rows = extraModules.stream()
                    .map(module -> new String[]{ module })
                    .toArray(String[][]::new);

            report.append(renderer.renderTable(new String[]{"Module supplémentaire"}, rows));
        }
    }

    /**
     * Classe interne pour stocker les résultats de l'analyse des modules
     */
    private static class ModuleAnalysisResult {
        final List<String> modulesCompletelyMissing = new ArrayList<>();
        final List<String> modulesExistButNotDeclared = new ArrayList<>();
        final List<String> modulesDeclaredButMissing = new ArrayList<>();
        final List<String> modulesExistButNoPom = new ArrayList<>();

        boolean hasIssues() {
            return !modulesCompletelyMissing.isEmpty() ||
                    !modulesExistButNotDeclared.isEmpty() ||
                    !modulesDeclaredButMissing.isEmpty() ||
                    !modulesExistButNoPom.isEmpty();
        }
    }
}