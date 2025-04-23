package org.elitost.maven.plugins.checkers;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;
import org.elitost.maven.plugins.utils.Symbols;

import java.io.File;
import java.util.*;
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

    // Constantes pour les messages
    private static final String MSG_MODULES_MISSING = "Modules attendus manquants (non déclarés et absents du disque) :";
    private static final String MSG_MODULES_NOT_DECLARED = "Modules existants mais non déclarés dans le pom.xml :";
    private static final String MSG_MODULES_DECLARED_MISSING = "Modules déclarés mais absents du disque :";
    private static final String MSG_MODULES_NO_POM = "Modules existants mais sans fichier pom.xml :";
    private static final String MSG_SUCCESS = "Tous les modules attendus sont présents et correctement déclarés.";
    private static final String MSG_ERROR = "Une erreur est survenue lors de la vérification des modules : `%s`";

    private Log log;
    private ReportRenderer renderer;

    /**
     * Constructeur requis pour le chargement SPI
     */
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
            Properties properties = project.getProperties();
            log.debug("[ModuleChecker] Chargement des propriétés Maven pour le projet : " + artifactId);

            List<String> moduleSuffixes = getConfiguredSuffixes(properties);
            log.debug("[ModuleChecker] Suffixes configurés : " + moduleSuffixes);

            Set<String> excludedModules = getConfiguredModules(properties, PROP_EXCLUDES, artifactId);
            log.debug("[ModuleChecker] Modules exclus : " + excludedModules);

            Set<String> optionalModules = getConfiguredModules(properties, PROP_OPTIONAL, artifactId);
            log.debug("[ModuleChecker] Modules optionnels : " + optionalModules);

            List<String> expectedModules = getExpectedModules(artifactId, moduleSuffixes, excludedModules);
            log.debug("[ModuleChecker] Modules attendus : " + expectedModules);

            ModuleAnalysisResult analysisResult = analyzeModules(project, expectedModules, optionalModules);

            if (analysisResult.hasIssues()) {
                appendDetailedIssuesReport(report, analysisResult);
            } else {
                appendSuccessMessage(report);
            }

            detectExtraModules(report, project, expectedModules, excludedModules);

        } catch (Exception e) {
            log.error("[ModuleChecker] Une exception s'est produite lors de la génération du rapport pour le projet : " + artifactId, e);
            appendErrorMessage(report, e);
        }

        report.append(renderer.closeIndentedSection());
        return report.toString();
    }

    private void appendDetailedIssuesReport(StringBuilder report, ModuleAnalysisResult analysisResult) {
        appendIssuesReport(report, analysisResult);
    }

    private void appendIssuesReport(StringBuilder report, ModuleAnalysisResult analysisResult) {
        appendIssueSectionIfNotEmpty(report, MSG_MODULES_MISSING, analysisResult.modulesCompletelyMissing,
                "💡 Ces modules sont attendus par convention. Tu dois les créer et les déclarer dans le pom.xml parent.");
        appendIssueSectionIfNotEmpty(report, MSG_MODULES_NOT_DECLARED, analysisResult.modulesExistButNotDeclared,
                "💡 Ces modules existent sur le disque mais ne sont pas déclarés dans la section `<modules>` du pom.xml parent.");
        appendIssueSectionIfNotEmpty(report, MSG_MODULES_DECLARED_MISSING, analysisResult.modulesDeclaredButMissing,
                "💡 Ces modules sont déclarés dans le pom.xml mais n'existent pas sur le disque.");
        appendIssueSectionIfNotEmpty(report, MSG_MODULES_NO_POM, analysisResult.modulesExistButNoPom,
                "💡 Ces modules existent sur le disque mais ne contiennent pas de fichier pom.xml.");
    }

    private void appendIssueSectionIfNotEmpty(StringBuilder report, String header, List<String> modules, String suggestion) {
        if (!modules.isEmpty()) {
            appendIssueSection(report, header, modules, suggestion);
        }
    }

    private void appendSuccessMessage(StringBuilder report) {
        String successMessage = Symbols.OK + MSG_SUCCESS;
        report.append(renderer.renderParagraph(successMessage));
        log.info("[ModuleChecker] " + successMessage);
    }

    private void appendErrorMessage(StringBuilder report, Exception e) {
        String errorMessage = String.format(MSG_ERROR, e.getMessage());
        report.append(renderer.renderError(errorMessage));
        log.error("[ModuleChecker] Détails de l'erreur : " + errorMessage, e);
    }

    /**
     * Récupère les suffixes de modules configurés ou utilise les valeurs par défaut
     */
    private List<String> getConfiguredSuffixes(Properties properties) {
        try {
            return Optional.ofNullable(properties.getProperty(PROP_SUFFIXES))
                    .map(suffixesProperty -> Arrays.stream(suffixesProperty.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList()))
                    .orElse(DEFAULT_MODULE_SUFFIXES);
        } catch (Exception e) {
            log.error("[ModuleChecker] Erreur lors de la récupération des suffixes configurés", e);
            throw e;
        }
    }

    /**
     * Récupère les modules configurés à partir d'une propriété Maven donnée.
     */
    private Set<String> getConfiguredModules(Properties properties, String propertyKey, String baseArtifactId) {
        try {
            return Optional.ofNullable(properties.getProperty(propertyKey))
                    .map(propertyValue -> Arrays.stream(propertyValue.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(suffix -> suffix.startsWith(baseArtifactId) ? suffix : baseArtifactId + suffix)
                            .collect(Collectors.toSet()))
                    .orElseGet(HashSet::new);
        } catch (Exception e) {
            log.error("[ModuleChecker] Erreur lors de la récupération des modules pour la propriété : " + propertyKey, e);
            throw e;
        }
    }

    /**
     * Génère la liste des modules attendus en fonction des suffixes et des exclusions
     */
    private List<String> getExpectedModules(String baseArtifactId, List<String> suffixes, Set<String> excludes) {
        try {
            return suffixes.stream()
                    .map(suffix -> baseArtifactId + suffix)
                    .filter(moduleName -> !excludes.contains(moduleName))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[ModuleChecker] Erreur lors de la génération des modules attendus", e);
            throw e;
        }
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
     * Ajoute une section de problème au rapport
     */
    private void appendIssueSection(StringBuilder report, String header, List<String> modules, String suggestion) {
        report.append(renderer.renderError(header));
        renderModuleList(report, modules, header);
        report.append(renderer.renderParagraph(suggestion));
    }

    /**
     * Affiche une liste de modules dans un tableau
     */
    private void renderModuleList(StringBuilder report, List<String> modules, String columnHeader) {
        Collections.sort(modules);
        String[][] rows = modules.stream()
                .map(module -> {
                    log.warn("[ModuleChecker] " + columnHeader + " : " + module);
                    return new String[]{module};
                })
                .toArray(String[][]::new);

        report.append(renderer.renderTable(new String[]{"📦 " + columnHeader}, rows));
    }

    /**
     * Détecte et signale les modules déclarés, mais non attendus par convention
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
            ));

            String[][] rows = extraModules.stream()
                    .map(module -> new String[]{module})
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

