package org.elitost.maven.plugins;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.elitost.maven.plugins.checkers.BasicInitializableChecker;
import org.elitost.maven.plugins.checkers.CustomChecker;
import org.elitost.maven.plugins.checkers.InitializableChecker;
import org.elitost.maven.plugins.factory.ReportRendererFactory;
import org.elitost.maven.plugins.renderers.ReportRenderer;
import org.elitost.maven.plugins.utils.Symbols;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Plugin Maven de vérification modulaire avec analyse avancée des projets multi-modules.
 * Ce plugin permet d'exécuter des vérifications personnalisées sur les modules d'un projet
 * et génère des rapports détaillés sur les résultats.
 *
 * @author Votre Nom
 * @since 1.0
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class ModuleCheckerMojo extends AbstractMojo {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("html", "markdown", "md", "text");
    private static final Set<String> TOP_LEVEL_ONLY_CHECKERS = Set.of("expectedModules", "propertyPresence");
    private static final String DEFAULT_CSS = "body { font-family: Arial, sans-serif; margin: 0; padding: 20px; line-height: 1.6; }\n" +
            "h1 { color: #2c3e50; }\n" +
            "h2 { color: #3498db; margin-top: 30px; border-bottom: 1px solid #eee; padding-bottom: 10px; }\n" +
            "h3 { color: #2980b9; }\n" +
            ".success { color: #27ae60; font-weight: bold; }\n" +
            ".warning { color: #f39c12; font-weight: bold; }\n" +
            ".error { color: #e74c3c; font-weight: bold; }\n" +
            "footer { margin-top: 30px; font-size: 0.8em; color: #7f8c8d; text-align: center; }";
    private final List<CustomChecker> checkers = new CopyOnWriteArrayList<>();
    private final AtomicInteger errorCount = new AtomicInteger(0);
    /**
     * Formats de sortie pour les rapports générés.
     * Les formats supportés sont : "html", "markdown" (ou "md"), et "text".
     * Plusieurs formats peuvent être spécifiés pour générer des rapports dans différents formats simultanément.
     * Si le format "md" est spécifié, il sera traité comme "markdown".
     * Si aucun format n'est spécifié ou si les formats spécifiés sont invalides, le format par défaut "html" sera utilisé.
     * Exemple de configuration :
     * <pre>
     * {@code
     * <configuration>
     *     <formats>
     *         <format>html</format>
     *         <format>markdown</format>
     *     </formats>
     * </configuration>
     * }
     * </pre>
     * <p>
     * Peut également être défini via la ligne de commande: {@code -Dformat=html,markdown}
     */
    @Parameter(property = "format", defaultValue = "html")
    private List<String> formats;
    /**
     * Liste des vérificateurs (checkers) à exécuter.
     * Si non spécifié, tous les vérificateurs disponibles seront exécutés.
     * Les vérificateurs sont identifiés par leur ID unique.
     * Exemple de configuration :
     * <pre>
     * {@code
     * <configuration>
     *     <checkersToRun>
     *         <checker>dependencyCheck</checker>
     *         <checker>propertyPresence</checker>
     *         <checker>interfaceConformity</checker>
     *     </checkersToRun>
     * </configuration>
     * }
     * </pre>
     * <p>
     * Peut également être défini via la ligne de commande: {@code -DcheckersToRun=dependencyCheck,propertyPresence}
     */
    @Parameter(property = "checkersToRun")
    private List<String> checkersToRun;
    /**
     * Liste des propriétés à vérifier par le vérificateur "propertyPresence".
     * Ces propriétés seront recherchées dans les fichiers de configuration des modules
     * pour s'assurer qu'elles sont correctement définies.
     * Exemple de configuration :
     * <pre>
     * {@code
     * <configuration>
     *     <propertiesToCheck>
     *         <property>project.version</property>
     *         <property>sonar.java.source</property>
     *         <property>maven.compiler.target</property>
     *     </propertiesToCheck>
     * </configuration>
     * }
     * </pre>
     * <p>
     * Peut également être défini via la ligne de commande: {@code -DpropertiesToCheck=project.version,sonar.java.source}
     * ou en tant que propriété système: {@code -DpropertiesToCheck=project.version,sonar.java.source}
     */
    @Parameter(property = "propertiesToCheck")
    private List<String> propertiesToCheck;
    /**
     * Indique si le build doit échouer lorsque des erreurs sont détectées par les vérificateurs.
     * Si défini à true, le build échouera avec une MojoFailureException lorsque au moins une erreur est trouvée.
     * Si défini à false (par défaut), le build continuera même si des erreurs sont détectées,
     * mais les erreurs seront tout de même rapportées dans les logs et les rapports générés.
     * Exemple de configuration :
     * <pre>
     * {@code
     * <configuration>
     *     <failOnError>true</failOnError>
     * </configuration>
     * }
     * </pre>
     * <p>
     * Peut également être défini via la ligne de commande: {@code -DfailOnError=true}
     */
    @Parameter(property = "failOnError", defaultValue = "false")
    private boolean failOnError;
    /**
     * Nom de base pour les fichiers de rapport générés.
     * Le nom final du fichier sera composé de ce nom de base, suivi d'un horodatage
     * et de l'extension correspondant au format du rapport.
     * Exemple : Si reportFileName="module-check-report", le fichier généré pourrait être
     * "module-check-report-20230421-143045.html"
     * Exemple de configuration :
     * <pre>
     * {@code
     * <configuration>
     *     <reportFileName>my-module-analysis</reportFileName>
     * </configuration>
     * }
     * </pre>
     * <p>
     * Peut également être défini via la ligne de commande: {@code -DreportFileName=my-module-analysis}
     */
    @Parameter(property = "reportFileName", defaultValue = "module-check-report")
    private String reportFileName;
    /**
     * Répertoire où les rapports générés seront écrits.
     * Si le répertoire n'existe pas, il sera créé.
     * Par défaut, les rapports sont générés dans le dossier "checker-reports" du répertoire de build du projet.
     * Exemple de configuration :
     * <pre>
     * {@code
     * <configuration>
     *     <reportOutputDirectory>${project.build.directory}/analysis-reports</reportOutputDirectory>
     * </configuration>
     * }
     * </pre>
     * <p>
     * Peut également être défini via la ligne de commande :
     * {@code -DreportOutputDirectory=/path/to/reports}
     */
    @Parameter(property = "reportOutputDirectory", defaultValue = "${project.build.directory}/checker-reports")
    private File reportOutputDirectory;
    /**
     * Délai d'expiration (en secondes) pour l'exécution de chaque vérificateur.
     * Si un vérificateur dépasse ce délai, son exécution sera interrompue et une erreur sera signalée.
     * Cette valeur doit être supérieure à zéro.
     * Exemple de configuration :
     * <pre>
     * {@code
     * <configuration>
     *     <checkerTimeout>60</checkerTimeout>
     * </configuration>
     * }
     * </pre>
     * <p>
     * Peut également être défini via la ligne de commande: {@code -DcheckerTimeout=60}
     */
    @Parameter(property = "checkerTimeout", defaultValue = "30")
    private int checkerTimeout;
    /**
     * Session du système de dépôt Maven.
     * Ce paramètre est injecté automatiquement par Maven et ne nécessite pas de configuration manuelle.
     */
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;
    /**
     * Système de dépôt Maven.
     * Ce composant est injecté automatiquement par Maven et ne nécessite pas de configuration manuelle.
     */
    @Component
    private RepositorySystem repoSystem;
    /**
     * Liste des dépôts distants du projet.
     * Ce paramètre est injecté automatiquement par Maven et ne nécessite pas de configuration manuelle.
     */
    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepositories;
    /**
     * Projet Maven courant.
     * Ce paramètre est injecté automatiquement par Maven et ne nécessite pas de configuration manuelle.
     */
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;
    /**
     * Liste des modules à exclure de l'analyse.
     * Les modules dont artifactId commence par l'une des valeurs spécifiées seront ignorés.
     * Utile pour exclure certains modules qui ne nécessitent pas de vérification
     * ou qui pourraient causer des problèmes lors de l'analyse.
     * Exemple de configuration :
     * <pre>
     * {@code
     * <configuration>
     *     <excludeModules>
     *         <module>legacy-</module>
     *         <module>test-</module>
     *     </excludeModules>
     * </configuration>
     * }
     * </pre>
     * <p>
     * Peut également être défini via la ligne de commande: {@code -DexcludeModules=legacy-,test-}
     */
    @Parameter(property = "excludeModules")
    private List<String> excludeModules;
    /**
     * Active le mode verbeux pour la sortie des logs.
     * Si défini à true, des informations supplémentaires seront affichées dans les logs,
     * notamment les détails des problèmes détectés dans chaque module.
     * Utile pour le débogage ou pour obtenir plus d'informations sur les vérifications effectuées.
     * Exemple de configuration :
     * <pre>
     * {@code
     * <configuration>
     *     <verbose>true</verbose>
     * </configuration>
     * }
     * </pre>
     * <p>
     * Peut également être défini via la ligne de commande: {@code -Dverbose=true}
     */
    @Parameter(property = "verbose", defaultValue = "false")
    private boolean verbose;
    private Log log;
    private boolean runAllCheckers;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.log = getLog();

        if (!isParentProject()) {
            log.info("🔍 Skipping non-parent project");
            return;
        }

        try {
            initializePlugin();
            validateParameters();
            generateReports();
            handleFailures();
        } catch (MojoExecutionException | MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException(Symbols.ERROR + "Plugin execution failed: " + e.getMessage(), e);
        }
    }

    private boolean isParentProject() {
        return "pom".equals(project.getPackaging()) &&
                project.getModules() != null &&
                !project.getModules().isEmpty();
    }

    private void initializePlugin() throws MojoExecutionException {
        loadSystemProperties();
        createReportDirectory();
        loadCheckers();
        logConfiguration();
        this.runAllCheckers = checkersToRun == null || checkersToRun.isEmpty();
    }

    private void loadSystemProperties() {
        if (propertiesToCheck == null) {
            propertiesToCheck = new ArrayList<>();
        }

        String sysProps = System.getProperty("propertiesToCheck");
        if (sysProps != null && !sysProps.trim().isEmpty()) {
            propertiesToCheck.addAll(Arrays.asList(sysProps.split(",")));
        }

        if (propertiesToCheck.isEmpty()) {
            log.warn(Symbols.WARNING + "No properties to check specified via -DpropertiesToCheck");
        }
    }

    private void createReportDirectory() throws MojoExecutionException {
        if (!reportOutputDirectory.exists() && !reportOutputDirectory.mkdirs()) {
            throw new MojoExecutionException(Symbols.ERROR + "Failed to create report directory: " +
                    reportOutputDirectory.getAbsolutePath());
        }
    }

    private void loadCheckers() {
        ServiceLoader.load(CustomChecker.class).forEach(checker -> {
            try {
                checkers.add(checker);
            } catch (Exception e) {
                log.error(Symbols.ERROR + "Failed to load checker: " + checker.getClass().getName(), e);
            }
        });

        if (checkers.isEmpty()) {
            log.warn(Symbols.WARNING + "No checkers found via SPI");
        }
    }

    private void logConfiguration() {
        log.info("📋 Plugin configuration:");
        log.info("- Output formats: " + (formats == null ? "default (html)" : formats));
        log.info("- Report directory: " + reportOutputDirectory.getAbsolutePath());
        log.info("- Fail on error: " + failOnError);
        log.info("- Verbose mode: " + verbose);

        List<String> checkerIds = checkers.stream()
                .map(CustomChecker::getId)
                .sorted()
                .collect(Collectors.toList());

        log.info("📦 Available checkers: " + checkerIds);

        if (checkersToRun != null && !checkersToRun.isEmpty()) {
            List<String> invalidCheckers = checkersToRun.stream()
                    .filter(id -> !checkerIds.contains(id))
                    .collect(Collectors.toList());

            if (!invalidCheckers.isEmpty()) {
                log.warn(Symbols.ERROR + "Invalid checkers specified: " + String.join(", ", invalidCheckers));
            }

            log.info(Symbols.OK + "Selected checkers: " + checkersToRun);
        } else {
            log.info(Symbols.OK + "All checkers will be executed");
        }
    }

    private void validateParameters() throws MojoExecutionException {
        if (checkerTimeout <= 0) {
            throw new MojoExecutionException("checkerTimeout must be > 0");
        }
        if (formats != null && formats.stream().anyMatch(format -> !SUPPORTED_FORMATS.contains(format.toLowerCase()))) {
            throw new MojoExecutionException("Invalid output format specified. Supported formats: " + SUPPORTED_FORMATS);
        }
        if (reportFileName == null || reportFileName.trim().isEmpty()) {
            throw new MojoExecutionException("reportFileName cannot be null or empty");
        }
        if (reportOutputDirectory == null) {
            throw new MojoExecutionException("reportOutputDirectory cannot be null");
        }
    }

    private void generateReports() throws Exception {
        for (String format : getValidFormats()) {
            ReportRenderer renderer = ReportRendererFactory.createRenderer(format);
            Map<MavenProject, String> reports = generateReportsForAllModules(renderer);
            String fullReport = buildAggregateReport(reports, renderer);
            writeReportFile(format, fullReport);
        }
    }

    private List<String> getValidFormats() {
        if (formats == null || formats.isEmpty()) {
            return List.of("html");
        }

        List<String> validFormats = formats.stream()
                .map(String::toLowerCase)
                .filter(SUPPORTED_FORMATS::contains)
                .map(f -> "md".equals(f) ? "markdown" : f)
                .distinct()
                .collect(Collectors.toList());

        if (validFormats.isEmpty()) {
            log.warn(Symbols.WARNING + " No valid formats specified. Using default: html");
            return List.of("html");
        }

        return validFormats;
    }

    private Map<MavenProject, String> generateReportsForAllModules(ReportRenderer renderer) {
        initializeCheckers(renderer);
        Map<MavenProject, String> reports = new ConcurrentHashMap<>();

        // Process parent module first
        reports.put(project, generateModuleReport(project, renderer, true));

        // Process child modules with a fixed thread pool
        int maxThreads = Math.min(Runtime.getRuntime().availableProcessors(), 4); // Limite à 4 threads max
        ExecutorService executor = Executors.newFixedThreadPool(maxThreads);

        try {
            List<Future<?>> futures = project.getCollectedProjects().stream()
                    .filter(this::shouldProcessModule)
                    .map(module -> executor.submit(() ->
                            reports.put(module, generateModuleReport(module, renderer, false))))
                    .collect(Collectors.toList());

            for (Future<?> future : futures) {
                future.get(); // Attendre la fin de chaque tâche
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(Symbols.ERROR + " Module analysis interrupted", e);
        } catch (ExecutionException e) {
            log.error(Symbols.ERROR + " Error during module analysis", e);
        } finally {
            executor.shutdown();
        }

        return reports;
    }

    private boolean shouldProcessModule(MavenProject module) {
        return excludeModules == null || excludeModules.isEmpty() ||
                excludeModules.stream().noneMatch(module.getArtifactId()::startsWith);
    }

    private void initializeCheckers(ReportRenderer renderer) {
        checkers.parallelStream().forEach(checker -> {
            try {
                if (checker instanceof BasicInitializableChecker) {
                    ((BasicInitializableChecker) checker).init(log, renderer);
                }
                if (checker instanceof InitializableChecker) {
                    ((InitializableChecker) checker).init(log, repoSystem, repoSession, remoteRepositories, renderer);
                }
            } catch (Exception e) {
                log.error(Symbols.ERROR + " Error initializing checker " + checker.getId(), e);
            }
        });
    }

    private String generateModuleReport(MavenProject module, ReportRenderer renderer, boolean isParent) {
        CheckerContext context = new CheckerContext(module, project, propertiesToCheck);
        StringBuilder report = new StringBuilder();

        report.append(renderer.renderHeader2("Module: " + module.getArtifactId()));

        getApplicableCheckers(module, isParent).parallelStream()
                .map(checker -> runCheckerWithTimeout(checker, context, renderer))
                .filter(content -> content != null && !content.isEmpty())
                .forEach(content -> report.append(content).append("\n"));

        return report.toString();
    }

    private List<CustomChecker> getApplicableCheckers(MavenProject module, boolean isParent) {
        return checkers.stream()
                .filter(checker -> runAllCheckers || checkersToRun.contains(checker.getId()))
                .filter(checker -> isCheckerApplicable(checker, module, isParent))
                .collect(Collectors.toList());
    }

    private boolean isCheckerApplicable(CustomChecker checker, MavenProject module, boolean isParent) {
        String checkerId = checker.getId();

        if (TOP_LEVEL_ONLY_CHECKERS.contains(checkerId) && !isParent) {
            return false;
        }

        return !"interfaceConformity".equals(checkerId) || module.getArtifactId().endsWith("-api");
    }

    private String runCheckerWithTimeout(CustomChecker checker, CheckerContext context, ReportRenderer renderer) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> executeChecker(checker, context, renderer));

        try {
            return future.get(checkerTimeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn(Symbols.TIMEOUT + "Checker timeout: " + checker.getId());
            future.cancel(true);
            return renderer.renderParagraph(Symbols.TIMEOUT + "Checker timeout: " + checker.getId());
        } catch (Exception e) {
            log.error(Symbols.ERROR + " Error executing checker " + checker.getId(), e);
            return renderer.renderParagraph(Symbols.ERROR + " Error: " + e.getMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    private String executeChecker(CustomChecker checker, CheckerContext context, ReportRenderer renderer) {
        try {
            String report = checker.generateReport(context);
            if (report.contains(Symbols.ERROR) || report.contains(Symbols.WARNING)) {
                errorCount.incrementAndGet();
                if (verbose) {
                    log.warn("Issue found by " + checker.getId() + " in " +
                            context.getCurrentModule().getArtifactId());
                }
            }
            return report;
        } catch (Exception e) {
            log.error(Symbols.ERROR + " Checker execution failed: " + checker.getId(), e);
            return renderer.renderError("Checker failed: " + e.getMessage());
        }
    }

    private String buildAggregateReport(Map<MavenProject, String> moduleReports, ReportRenderer renderer) {
        StringBuilder content = new StringBuilder();

        // Header
        content.append(renderer.renderHeader1("Module Verification Report"));
        content.append(renderer.renderParagraph("Date: " +
                new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date())));
        content.append(renderer.renderParagraph("Project: " + project.getName() +
                " (" + project.getGroupId() + ":" + project.getArtifactId() + ")"));

        // Summary
        if (errorCount.get() > 0) {
            content.append(renderer.renderHeader2("Summary"));
            content.append(renderer.renderParagraph(Symbols.WARNING + " " + errorCount.get() + " issues detected"));
        } else {
            content.append(renderer.renderParagraph(Symbols.OK + "No issues detected"));
        }

        // Parent report first
        content.append(moduleReports.get(project));

        // Child modules sorted by artifactId
        moduleReports.entrySet().stream()
                .filter(e -> e.getKey() != project)
                .sorted(Comparator.comparing(e -> e.getKey().getArtifactId()))
                .forEach(e -> content.append(e.getValue()));

        return content.toString();
    }

    private void writeReportFile(String format, String content) throws MojoExecutionException {
        String extension = getFileExtension(format);
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File outputFile = new File(reportOutputDirectory, reportFileName + "-" + timestamp + "." + extension);

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write(formatReportContent(format, content));
            log.info("📄 Report generated: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            throw new MojoExecutionException(Symbols.ERROR + " Failed to write " + format + " report", e);
        }
    }

    private String getFileExtension(String format) {
        switch (format.toLowerCase()) {
            case "markdown":
                return "md";
            case "text":
                return "txt";
            default:
                return "html";
        }
    }

    private String formatReportContent(String format, String content) {
        switch (format.toLowerCase()) {
            case "markdown":
                return "# Verification Report\n\n" + content;
            case "text":
                return "VERIFICATION REPORT\n====================\n\n" + content;
            case "html":
            default:
                return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n" +
                        "<meta charset=\"UTF-8\">\n" +
                        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                        "<title>Verification Report</title>\n" +
                        "<style>" + getCss() + "</style>\n</head>\n<body>\n" +
                        "<h1>Verification Report</h1>\n" + content +
                        "\n<footer>Generated on " +
                        new SimpleDateFormat("MM/dd/yyyy 'at' HH:mm:ss").format(new Date()) +
                        " with module-checker plugin</footer>\n</body>\n</html>";
        }
    }

    private String getCss() {
        try (InputStream is = getClass().getResourceAsStream("/assets/css/style.css")) {
            return is != null ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : DEFAULT_CSS;
        } catch (IOException e) {
            log.warn(Symbols.WARNING + "Could not load CSS, using default style", e);
            return DEFAULT_CSS;
        }
    }

    private void handleFailures() throws MojoFailureException {
        if (failOnError && errorCount.get() > 0) {
            log.error(Symbols.ERROR + errorCount.get() + " errors detected. Check reports for details.");
            throw new MojoFailureException(Symbols.ERROR + errorCount.get() + " errors detected. Build failed.");
        } else if (errorCount.get() > 0) {
            log.warn(Symbols.WARNING + errorCount.get() + " errors detected. Build will continue.");
        }
    }
}
