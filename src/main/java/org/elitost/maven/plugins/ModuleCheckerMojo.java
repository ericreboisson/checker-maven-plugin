package org.elitost.maven.plugins;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.elitost.maven.plugins.checkers.BasicInitializableChecker;
import org.elitost.maven.plugins.checkers.CustomChecker;
import org.elitost.maven.plugins.checkers.InitializableChecker;
import org.elitost.maven.plugins.factory.ReportRendererFactory;
import org.elitost.maven.plugins.renderers.ReportRenderer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Plugin Maven de vérification modulaire avec analyse avancée des projets multi-modules.
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

    @Parameter(property = "format", defaultValue = "html")
    private List<String> formats;

    @Parameter(property = "checkersToRun")
    private List<String> checkersToRun;

    @Parameter(property = "propertiesToCheck")
    private List<String> propertiesToCheck;

    @Parameter(property = "failOnError", defaultValue = "false")
    private boolean failOnError;

    @Parameter(property = "reportFileName", defaultValue = "module-check-report")
    private String reportFileName;

    @Parameter(property = "reportOutputDirectory", defaultValue = "${project.build.directory}/checker-reports")
    private File reportOutputDirectory;

    @Parameter(property = "checkerTimeout", defaultValue = "30")
    private int checkerTimeout;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepositories;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(property = "excludeModules")
    private List<String> excludeModules;

    @Parameter(property = "verbose", defaultValue = "false")
    private boolean verbose;

    private final List<CustomChecker> checkers = new CopyOnWriteArrayList<>();
    private final AtomicInteger errorCount = new AtomicInteger(0);
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
            throw new MojoExecutionException("❌ Plugin execution failed: " + e.getMessage(), e);
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
            log.warn("⚠️ No properties to check specified via -DpropertiesToCheck");
        }
    }

    private void createReportDirectory() throws MojoExecutionException {
        if (!reportOutputDirectory.exists() && !reportOutputDirectory.mkdirs()) {
            throw new MojoExecutionException("❌ Failed to create report directory: " +
                    reportOutputDirectory.getAbsolutePath());
        }
    }

    private void loadCheckers() {
        ServiceLoader.load(CustomChecker.class).forEach(checker -> {
            try {
                checkers.add(checker);
            } catch (Exception e) {
                log.error("❌ Failed to load checker: " + checker.getClass().getName(), e);
            }
        });

        if (checkers.isEmpty()) {
            log.warn("⚠️ No checkers found via SPI");
        }
    }

    private void logConfiguration() {
        log.info("📋 Plugin configuration:");
        log.info("- Output formats: " + formats);
        log.info("- Report directory: " + reportOutputDirectory.getAbsolutePath());

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
                log.warn("❌ Invalid checkers specified: " + String.join(", ", invalidCheckers));
            }

            log.info("✅ Selected checkers: " + checkersToRun);
        } else {
            log.info("✅ All checkers will be executed");
        }
    }

    private void validateParameters() throws MojoExecutionException {
        if (checkerTimeout <= 0) {
            throw new MojoExecutionException("checkerTimeout must be > 0");
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
            log.warn("⚠️ No valid formats specified. Using default: html");
            return List.of("html");
        }

        return validFormats;
    }

    private Map<MavenProject, String> generateReportsForAllModules(ReportRenderer renderer) {
        initializeCheckers(renderer);
        Map<MavenProject, String> reports = new ConcurrentHashMap<>();

        // Process parent first
        reports.put(project, generateModuleReport(project, renderer, true));

        // Process child modules in parallel
        ForkJoinPool customPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        try {
            customPool.submit(() ->
                    project.getCollectedProjects().parallelStream()
                            .filter(this::shouldProcessModule)
                            .forEach(module ->
                                    reports.put(module, generateModuleReport(module, renderer, false))
                            )
            ).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ Module analysis interrupted", e);
        } catch (ExecutionException e) {
            log.error("❌ Error during module analysis", e);
        } finally {
            customPool.shutdown();
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
                log.error("❌ Error initializing checker " + checker.getId(), e);
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

        if ("interfaceConformity".equals(checkerId) && !module.getArtifactId().endsWith("-api")) {
            return false;
        }

        return true;
    }

    private String runCheckerWithTimeout(CustomChecker checker, CheckerContext context, ReportRenderer renderer) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> executeChecker(checker, context, renderer));

        try {
            return future.get(checkerTimeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("⏱️ Checker timeout: " + checker.getId());
            future.cancel(true);
            return renderer.renderParagraph("⏱️ Checker timeout: " + checker.getId());
        } catch (Exception e) {
            log.error("❌ Error executing checker " + checker.getId(), e);
            return renderer.renderParagraph("❌ Error: " + e.getMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    private String executeChecker(CustomChecker checker, CheckerContext context, ReportRenderer renderer) {
        try {
            String report = checker.generateReport(context);
            if (report.contains("❌") || report.contains("⚠️")) {
                errorCount.incrementAndGet();
                if (verbose) {
                    log.warn("Issue found by " + checker.getId() + " in " +
                            context.getCurrentModule().getArtifactId());
                }
            }
            return report;
        } catch (Exception e) {
            log.error("❌ Checker execution failed: " + checker.getId(), e);
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
            content.append(renderer.renderParagraph("⚠️ " + errorCount.get() + " issues detected"));
        } else {
            content.append(renderer.renderParagraph("✅ No issues detected"));
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
            throw new MojoExecutionException("❌ Failed to write " + format + " report", e);
        }
    }

    private String getFileExtension(String format) {
        switch (format.toLowerCase()) {
            case "markdown": return "md";
            case "text": return "txt";
            default: return "html";
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
            log.warn("⚠️ Could not load CSS, using default style", e);
            return DEFAULT_CSS;
        }
    }

    private void handleFailures() throws MojoFailureException {
        if (failOnError && errorCount.get() > 0) {
            throw new MojoFailureException("❌ " + errorCount.get() + " errors detected. Check reports for details.");
        }
    }
}