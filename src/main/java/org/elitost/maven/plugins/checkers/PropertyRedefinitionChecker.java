package org.elitost.maven.plugins.checkers;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;

import java.util.*;

/**
 * Vérifie si des propriétés définies dans la chaîne de POM parents sont redéfinies dans les POM enfants.
 * Parcourt récursivement toute la hiérarchie des parents (jusqu'à spring-boot-dependencies ou autre).
 * Génère un rapport listant les propriétés redéfinies avec leurs valeurs d'origine et redéfinies.
 */
public class PropertyRedefinitionChecker implements CustomChecker, InitializableChecker {

    private static final String CHECKER_ID = "propertyRedefinition";
    private static final String ERROR_PREFIX = "[PropertyRedefinitionChecker]";
    // Liste des propriétés à ignorer (celles qu'on s'attend à voir redéfinies)
    private final Set<String> ignoredProperties = new HashSet<>(Arrays.asList(
            "project.artifactId",
            "project.version",
            "project.name",
            "start-class"  // Souvent redéfinie dans les projets Spring Boot
    ));
    private Log log;
    private ReportRenderer renderer;

    public PropertyRedefinitionChecker() {
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
            MavenProject currentModule = checkerContext.getCurrentModule();

            // Récupérer toutes les propriétés des parents
            Map<String, ParentPropertyInfo> parentPropertiesMap = collectParentProperties(currentModule);

            if (parentPropertiesMap.isEmpty()) {
                log.debug(ERROR_PREFIX + " Aucun parent trouvé pour " + currentModule.getArtifactId());
                return "";
            }

            Properties currentProps = currentModule.getProperties();

            List<PropertyRedefinitionResult> redefinedProperties = checkRedefinedProperties(
                    parentPropertiesMap, currentProps, currentModule.getArtifactId());

            if (redefinedProperties.isEmpty()) {
                return "";
            }

            return buildReport(currentModule.getArtifactId(), redefinedProperties);
        } catch (Exception e) {
            log.error(ERROR_PREFIX + " Exception levée", e);
            return renderErrorReport(e);
        }
    }

    /**
     * Collecte récursivement toutes les propriétés de tous les parents
     */
    private Map<String, ParentPropertyInfo> collectParentProperties(MavenProject project) {
        Map<String, ParentPropertyInfo> result = new HashMap<>();
        collectParentPropertiesRecursive(project.getParent(), result, 1);
        return result;
    }

    /**
     * Méthode récursive pour collecter les propriétés dans toute la hiérarchie des parents
     */
    private void collectParentPropertiesRecursive(MavenProject parent, Map<String, ParentPropertyInfo> result, int level) {
        if (parent == null) {
            return;
        }

        // Pour chaque propriété du parent actuel
        Properties parentProps = parent.getProperties();
        for (String key : parentProps.stringPropertyNames()) {
            // N'ajouter la propriété que si elle n'existe pas déjà dans un parent plus proche
            if (!result.containsKey(key)) {
                String value = parentProps.getProperty(key);
                result.put(key, new ParentPropertyInfo(value, parent.getArtifactId(), level));
            }
        }

        // Continuer avec le parent du parent
        collectParentPropertiesRecursive(parent.getParent(), result, level + 1);
    }

    private List<PropertyRedefinitionResult> checkRedefinedProperties(
            Map<String, ParentPropertyInfo> parentPropsMap, Properties currentProps, String moduleId) {

        List<PropertyRedefinitionResult> results = new ArrayList<>();

        // Pour chaque propriété du parent, vérifie si elle est redéfinie dans l'enfant
        for (Map.Entry<String, ParentPropertyInfo> entry : parentPropsMap.entrySet()) {
            String key = entry.getKey();
            ParentPropertyInfo info = entry.getValue();

            // Ignorer les propriétés qu'on s'attend à voir redéfinies
            if (ignoredProperties.contains(key)) {
                continue;
            }

            if (currentProps.containsKey(key)) {
                String parentValue = info.getValue();
                String currentValue = currentProps.getProperty(key);

                // Si la valeur est différente, c'est une redéfinition
                if (!parentValue.equals(currentValue)) {
                    log.warn(String.format("%s ⚠️ Propriété redéfinie: %s dans %s (originale dans %s: %s, actuelle: %s)",
                            ERROR_PREFIX, key, moduleId, info.getSourceArtifactId(), parentValue, currentValue));
                    results.add(new PropertyRedefinitionResult(key, parentValue, currentValue, info.getSourceArtifactId(), info.getLevel()));
                }
            }
        }

        // Trier par niveau puis par clé pour une meilleure lisibilité
        results.sort(Comparator.comparing(PropertyRedefinitionResult::getLevel)
                .thenComparing(PropertyRedefinitionResult::getKey));

        return results;
    }

    private String buildReport(String artifactId, List<PropertyRedefinitionResult> redefinedProperties) {
        StringBuilder report = new StringBuilder();

        report.append(renderer.renderHeader3("🔄 Propriétés redéfinies dans `" + artifactId + "`"));
        report.append(renderer.openIndentedSection());
        report.append(renderer.renderWarning("Les propriétés suivantes sont redéfinies par rapport à la chaîne de POM parents :"));

        String[][] tableData = redefinedProperties.stream()
                .map(result -> new String[]{
                        result.getKey(),
                        result.getSourceArtifactId(),
                        result.getParentValue(),
                        result.getCurrentValue()
                })
                .toArray(String[][]::new);

        report.append(renderer.renderTable(
                new String[]{"Clé", "Parent Source", "Valeur Originale", "Valeur Redéfinie"},
                tableData));

        report.append(renderer.closeIndentedSection());
        return report.toString();
    }

    private String renderErrorReport(Exception e) {
        return renderer.renderError("❌ Une erreur est survenue : " + e.getMessage());
    }

    // Classe interne pour stocker les informations sur les propriétés des parents
    private static class ParentPropertyInfo {
        private final String value;
        private final String sourceArtifactId;
        private final int level; // Le niveau de profondeur dans la hiérarchie (1 = parent direct, 2 = grand-parent, etc.)

        ParentPropertyInfo(String value, String sourceArtifactId, int level) {
            this.value = value;
            this.sourceArtifactId = sourceArtifactId;
            this.level = level;
        }

        String getValue() {
            return value;
        }

        String getSourceArtifactId() {
            return sourceArtifactId;
        }

        int getLevel() {
            return level;
        }
    }

    // Classe interne pour gérer les résultats
    private static class PropertyRedefinitionResult {
        private final String key;
        private final String parentValue;
        private final String currentValue;
        private final String sourceArtifactId;
        private final int level;

        PropertyRedefinitionResult(String key, String parentValue, String currentValue, String sourceArtifactId, int level) {
            this.key = key;
            this.parentValue = parentValue;
            this.currentValue = currentValue;
            this.sourceArtifactId = sourceArtifactId;
            this.level = level;
        }

        String getKey() {
            return key;
        }

        String getParentValue() {
            return parentValue;
        }

        String getCurrentValue() {
            return currentValue;
        }

        String getSourceArtifactId() {
            return sourceArtifactId;
        }

        int getLevel() {
            return level;
        }
    }
}