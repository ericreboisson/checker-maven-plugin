package org.elitost.maven.plugins.checkers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.*;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.elitost.maven.plugins.CheckerContext;
import org.elitost.maven.plugins.renderers.ReportRenderer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Vérifie les fichiers application.yml en utilisant un schéma de validation.
 * Liste également les propriétés non définies dans le schéma comme points d'attention.
 */
public class YamlSchemaValidatorChecker implements CustomChecker, InitializableChecker {

    private static final String APPLICATION_YML = "application.yml";
    private static final String APPLICATION_YAML = "application.yaml";
    private final List<String> excludedProperties = List.of(
            "logging.level" // Ajoutez ici les propriétés ou préfixes à exclure
    );
    private Log log;
    private ReportRenderer renderer;
    private JsonSchema schema;

    public YamlSchemaValidatorChecker() {
    }

    @Override
    public void init(Log log,
                     RepositorySystem repoSystem,
                     RepositorySystemSession session,
                     List<RemoteRepository> remoteRepositories,
                     ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;

        try {
            this.schema = loadValidationSchema();
            if (this.schema == null) {
                throw new IllegalStateException("Le schéma de validation n'a pas pu être chargé.");
            }
            log.info("Schéma de validation YAML chargé avec succès");
        } catch (Exception e) {
            log.error("Erreur lors du chargement du schéma de validation", e);
            this.schema = null;
        }
    }

    @Override
    public String getId() {
        return "yamlSchemaValidator";
    }

    @Override
    public String generateReport(CheckerContext checkerContext) {
        try {
            return checkApplicationYaml(checkerContext.getCurrentModule().getFile().getParentFile());
        } catch (Exception e) {
            log.error("Erreur lors de la vérification du fichier application.yml", e);
            return renderer.renderError("Erreur lors de la vérification du fichier application.yml : " + e.getMessage());
        }
    }

    private JsonSchema loadValidationSchema() throws IOException {
        try (InputStream schemaInputStream = getClass().getClassLoader().getResourceAsStream("schema/application.schema.json")) {
            if (schemaInputStream == null) {
                throw new IOException("Le schéma de validation YAML n'a pas été trouvé dans le classpath : schema/application.schema.json");
            }
            log.info("Schéma de validation chargé depuis le classpath : schema/application.schema.json");

            JsonSchemaFactory factory = JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
                    .addMetaSchema(JsonMetaSchema.getV7())
                    .build();

            return factory.getSchema(schemaInputStream);
        }
    }

    private String checkApplicationYaml(File moduleDir) {
        if (moduleDir == null) {
            return renderer.renderWarning("Impossible de déterminer le répertoire du module");
        }

        List<File> configFiles = findApplicationYamlFiles(moduleDir);

        if (configFiles.isEmpty()) {
            return "";
        }

        StringBuilder report = new StringBuilder();
        boolean hasIssues = false;

        for (File configFile : configFiles) {
            try {
                Set<ValidationMessage> validationErrors = validateYamlAgainstSchema(configFile);

                if (!validationErrors.isEmpty()) {
                    hasIssues = true;
                    report.append(generateValidationErrorReport(configFile, validationErrors));
                }

                Map<String, Object> yamlContent = parseYamlFile(configFile);
                List<String> additionalProperties = findAdditionalProperties(yamlContent);

                if (!additionalProperties.isEmpty()) {
                    hasIssues = true;
                    report.append(generateAdditionalPropertiesReport(configFile, additionalProperties));
                }

            } catch (Exception e) {
                hasIssues = true;
                log.error("Erreur lors de la validation du fichier " + configFile.getAbsolutePath(), e);
                report.append(renderer.renderError("Erreur de validation pour " + configFile.getName() + ": " + e.getMessage()));
            }
        }

        if (!hasIssues) {
            log.info("Tous les fichiers YAML validés avec succès selon le schéma.");
        }

        return report.toString();
    }

    private Set<ValidationMessage> validateYamlAgainstSchema(File yamlFile) throws IOException {
        if (this.schema == null) {
            throw new IllegalStateException("Le schéma de validation n'est pas initialisé.");
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JsonNode yamlNode = mapper.readTree(yamlFile);

        return schema.validate(yamlNode);
    }

    private List<File> findApplicationYamlFiles(File moduleDir) {
        List<File> configFiles = new ArrayList<>();

        List<String> configPaths = Arrays.asList(
                "src/main/resources",
                "src/main/resources/config",
                "config",
                "src/test/resources",
                "src/test/resources/config"
        );

        for (String path : configPaths) {
            File configDir = new File(moduleDir, path);
            if (configDir.exists() && configDir.isDirectory()) {
                addYamlIfExists(configFiles, configDir, APPLICATION_YML);
                addYamlIfExists(configFiles, configDir, APPLICATION_YAML);
            }
        }

        return configFiles;
    }

    private void addYamlIfExists(List<File> files, File directory, String filename) {
        File file = new File(directory, filename);
        if (file.exists() && file.isFile()) {
            files.add(file);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYamlFile(File yamlFile) {
        try (FileInputStream fis = new FileInputStream(yamlFile)) {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            return mapper.readValue(fis, Map.class);
        } catch (IOException e) {
            log.error("Erreur lors de la lecture du fichier YAML: " + yamlFile.getAbsolutePath(), e);
            return Collections.emptyMap();
        }
    }

    private List<String> findAdditionalProperties(Map<String, Object> yamlContent) {
        List<String> additionalProperties = new ArrayList<>();
        detectAdditionalProperties("", yamlContent, additionalProperties);
        return filterExcludedProperties(additionalProperties);
    }

    @SuppressWarnings("unchecked")
    private void detectAdditionalProperties(String prefix, Map<String, Object> map, List<String> additionalProperties) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            String fullPath = prefix.isEmpty() ? key : prefix + "." + key;

            if (!isPropertyDefinedInSchema(fullPath)) {
                if (!(value instanceof Map) && !(value instanceof List)) {
                    additionalProperties.add(fullPath);
                } else if (value instanceof List) {
                    additionalProperties.add(fullPath);
                }
            }

            if (value instanceof Map) {
                detectAdditionalProperties(fullPath, (Map<String, Object>) value, additionalProperties);
            }
        }
    }

    private boolean isPropertyDefinedInSchema(String propertyPath) {
        if (schema == null) {
            throw new IllegalStateException("Le schéma de validation n'est pas initialisé.");
        }

        String[] pathSegments = propertyPath.split("\\.");
        JsonNode currentNode = schema.getSchemaNode();

        for (String segment : pathSegments) {
            if (!currentNode.has("properties") || !currentNode.get("properties").has(segment)) {
                return false;
            }
            currentNode = currentNode.get("properties").get(segment);
        }

        return true;
    }

    private String generateValidationErrorReport(File yamlFile, Set<ValidationMessage> validationErrors) {
        StringBuilder reportSection = new StringBuilder();

        reportSection.append(renderer.renderHeader3("❌ Validation échouée pour: " + yamlFile.getName()));
        reportSection.append(renderer.openIndentedSection());

        reportSection.append(renderer.renderWarning("Le fichier ne respecte pas le schéma de validation:"));
        reportSection.append(renderer.openIndentedSection());

        for (ValidationMessage error : validationErrors) {
            String path = error.getInstanceLocation().toString().replaceFirst("^\\$\\.", "");
            String message = error.getMessage();

            if (error.getDetails() != null && error.getDetails().containsKey("errorMessage")) {
                message = error.getDetails().get("errorMessage").toString();
            }

            String foundValue = extractValueFromPath(error.getInstanceLocation().toString(), yamlFile);
            reportSection.append(renderer.renderParagraph("Propriété '" + path + "' : " + message + " (valeur trouvée : '" + foundValue + "')."));
        }

        reportSection.append(renderer.closeIndentedSection());
        reportSection.append(renderer.closeIndentedSection());

        return reportSection.toString();
    }

    private String generateAdditionalPropertiesReport(File yamlFile, List<String> additionalProperties) {
        StringBuilder reportSection = new StringBuilder();

        reportSection.append(renderer.renderHeader3("⚠️ Propriétés supplémentaires détectées dans: " + yamlFile.getName()));
        reportSection.append(renderer.openIndentedSection());

        reportSection.append(renderer.renderWarning("Les propriétés suivantes ne sont pas définies dans le schéma:"));
        reportSection.append(renderer.openIndentedSection());

        for (String property : additionalProperties) {
            reportSection.append(renderer.renderParagraph(property));
        }

        reportSection.append(renderer.closeIndentedSection());
        reportSection.append(renderer.closeIndentedSection());

        return reportSection.toString();
    }

    private String extractValueFromPath(String path, File yamlFile) {
        try {
            if (path.startsWith("$."))
                path = path.substring(2);

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            JsonNode rootNode = mapper.readTree(yamlFile);

            String[] segments = path.split("\\.");
            JsonNode currentNode = rootNode;

            for (String segment : segments) {
                if (segment.contains("[")) {
                    String propertyName = segment.substring(0, segment.indexOf("["));
                    int index = Integer.parseInt(segment.substring(segment.indexOf("[") + 1, segment.indexOf("]")));

                    if (currentNode.has(propertyName)) {
                        currentNode = currentNode.get(propertyName);
                        if (currentNode.isArray() && currentNode.size() > index) {
                            currentNode = currentNode.get(index);
                        } else {
                            return "Valeur introuvable pour le chemin : " + path;
                        }
                    } else {
                        return "Valeur introuvable pour le chemin : " + path;
                    }
                } else {
                    if (currentNode.has(segment)) {
                        currentNode = currentNode.get(segment);
                    } else {
                        return "Valeur introuvable pour le chemin : " + path;
                    }
                }
            }

            if (currentNode.isArray()) {
                StringBuilder values = new StringBuilder();
                for (JsonNode item : currentNode) {
                    values.append("- ").append(item.asText()).append("\n");
                }
                return values.toString().trim();
            }

            return currentNode.isValueNode() ? currentNode.asText() : currentNode.toString();
        } catch (IOException | NumberFormatException e) {
            log.error("Erreur lors de l'extraction de la valeur pour le chemin: " + path, e);
            return "Erreur lors de la lecture du fichier YAML";
        }
    }

    private List<String> filterExcludedProperties(List<String> properties) {
        List<String> filteredProperties = new ArrayList<>();
        for (String property : properties) {
            boolean isExcluded = excludedProperties.stream()
                    .anyMatch(property::startsWith);
            if (!isExcluded) {
                filteredProperties.add(property);
            }
        }
        return filteredProperties;
    }
}