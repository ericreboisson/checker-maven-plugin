package org.elitost.maven.plugins.checkers;

import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Vérifie la présence de propriétés spécifiques dans un projet Maven.
 * Génère un rapport listant les propriétés manquantes avec des suggestions si disponibles.
 */
public class PropertyPresenceChecker implements CustomChecker, InitializableChecker {

    private static final String CHECKER_ID = "propertyPresence";
    private static final String ERROR_PREFIX = "[PropertyChecker]";
    private final PropertySuggester propertySuggester;
    private Log log;
    private ReportRenderer renderer;

    // Nécessaire pour l'injection de dépendances
    public PropertyPresenceChecker() {
        this.propertySuggester = new DefaultPropertySuggester();
    }

    @Override
    public void init(Log log,
                     RepositorySystem repoSystem,
                     RepositorySystemSession session,
                     List<RemoteRepository> remoteRepositories,
                     ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    @Override
    public String getId() {
        return CHECKER_ID;
    }

    @Override
    public String generateReport(CheckerContext checkerContext) {
        try {
            String artifactId = checkerContext.getCurrentModule().getArtifactId();
            Properties props = checkerContext.getCurrentModule().getProperties();
            List<String> propertiesToCheck = checkerContext.getPropertiesToCheck();

            // Élimination des doublons dans la liste des propriétés à vérifier
            Set<String> uniquePropertiesToCheck = new LinkedHashSet<>(propertiesToCheck);

            List<PropertyCheckResult> missingProperties = checkMissingProperties(props, uniquePropertiesToCheck);

            if (missingProperties.isEmpty()) {
                return "";
            }

            return buildReport(artifactId, missingProperties);
        } catch (Exception e) {
            log.error(ERROR_PREFIX + " Exception levée", e);
            return renderErrorReport(e);
        }
    }

    private List<PropertyCheckResult> checkMissingProperties(Properties props, Set<String> propertiesToCheck) {
        return propertiesToCheck.stream()
                .filter(key -> !props.containsKey(key))
                .map(key -> {
                    String suggestion = propertySuggester.suggestValue(key);
                    log.warn(String.format("%s ❌ Propriété manquante: %s %s",
                            ERROR_PREFIX, key,
                            suggestion != null ? "(Suggestion: " + suggestion + ")" : ""));
                    return new PropertyCheckResult(key, suggestion);
                })
                .collect(Collectors.toList());
    }

    private String buildReport(String artifactId, List<PropertyCheckResult> missingProperties) {
        StringBuilder report = new StringBuilder();

        report.append(renderer.renderHeader3("🔧 Propriétés manquantes dans `" + artifactId + "`"));
        report.append(renderer.openIndentedSection());
        report.append(renderer.renderError("Les propriétés suivantes sont manquantes :"));

        String[][] tableData = missingProperties.stream()
                .map(result -> {
                    String suggestionText = result.getSuggestion() != null ?
                            "Suggestion: " + result.getSuggestion() : "";
                    return new String[]{
                            result.getKey(),
                            "Manquante",
                            suggestionText
                    };
                })
                .toArray(String[][]::new);

        report.append(renderer.renderTable(
                new String[]{"Clé", "Statut", "Suggestion"},
                tableData));

        report.append(renderer.closeIndentedSection());
        return report.toString();
    }

    private String renderErrorReport(Exception e) {
        return renderer.renderError("❌ Une erreur est survenue : " + e.getMessage());
    }

    // Interface pour les suggestions de valeurs
    public interface PropertySuggester {
        String suggestValue(String propertyKey);
    }

    // Classe interne pour gérer les résultats
    private static class PropertyCheckResult {
        private final String key;
        private final String suggestion;

        PropertyCheckResult(String key, String suggestion) {
            this.key = key;
            this.suggestion = suggestion;
        }

        String getKey() {
            return key;
        }

        String getSuggestion() {
            return suggestion;
        }
    }

    // Implémentation par défaut
    private static class DefaultPropertySuggester implements PropertySuggester {
        private static final String[][] KNOWN_PROPERTIES = {
                {"project.build.sourceEncoding", "UTF-8"},
                {"maven.compiler.source", "11"},
                {"maven.compiler.target", "11"},
                {"java.version", "11"},
                {"component.name", "XXX-0007"}
        };

        @Override
        public String suggestValue(String propertyKey) {
            for (String[] known : KNOWN_PROPERTIES) {
                if (known[0].equals(propertyKey)) {
                    return known[1];
                }
            }
            return null;
        }
    }
}