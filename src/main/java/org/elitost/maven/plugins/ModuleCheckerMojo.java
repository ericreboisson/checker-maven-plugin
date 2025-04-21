package org.elitost.maven.plugins;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.elitost.maven.plugins.checkers.BasicInitializableChecker;
import org.elitost.maven.plugins.checkers.CustomChecker;
import org.elitost.maven.plugins.checkers.InitializableChecker;
import org.elitost.maven.plugins.renderers.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Plugin Maven de vérification modulaire destiné à analyser la structure d’un projet multi-modules.
 * Ce plugin exécute dynamiquement une série de checkers chargés via SPI, afin de détecter des problèmes courants dans les fichiers pom.xml, les dépendances, etc.
 * Fonctionnalités :
 * - Découverte dynamique des checkers via SPI
 * - Sélection des checkers via -DcheckersToRun=...
 * - Support multi-format de rendu (HTML, Markdown, Texte)
 * - Analyse récursive de tous les modules déclarés dans le projet parent
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.NONE)
@Execute(goal = "check")
public class ModuleCheckerMojo extends AbstractMojo {

    /** Format de sortie du rapport (html, markdown, text). */
    @Parameter(property = "format", defaultValue = "html")
    private List<String> format;

    /** Liste d'identifiants de checkers à exécuter. */
    @Parameter(property = "checkersToRun")
    private List<String> checkersToRun;

    /** Session Aether pour la résolution des dépendances. */
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    /** Composant Aether injecté. */
    @Component
    private RepositorySystem repoSystem;

    /** Dépôts distants pour la résolution. */
    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepositories;

    /** Projet Maven courant (parent). */
    @Parameter(defaultValue = "${project}")
    private MavenProject project;

    /** Propriétés Maven à vérifier, issues du système. */
    @Parameter
    private List<String> propertiesToCheck;

    private final List<CustomChecker> checkers = new ArrayList<>();
    private Log log;
    private boolean runAll;

    /**
     * Point d'entrée principal du plugin.
     * @throws MojoExecutionException en cas d'erreur bloquante
     */
    @Override
    public void execute() throws MojoExecutionException {
        this.log = getLog();

        // Si ce n'est pas un pom parent, on arrête le plugin
        if (!isParentPom()) {
            log.info("🔍 Ce n'est pas le pom parent, le plugin ne s'exécute pas ici.");
            return;
        }

        enrichPropertiesFromSystem();  // Enrichit les propriétés à vérifier depuis la ligne de commande
        initCheckers();  // Charge et initialise-les checkers dynamiquement via SPI
        logAvailableCheckers();  // Affiche les checkers disponibles

        runAll = checkersToRun == null || checkersToRun.isEmpty();

        // Rendu et génération du rapport
        ReportRenderer renderer = resolveRenderer();
        StringBuilder content = generateReportContent(project, renderer);
        generateModuleReports(renderer, content);

        // Écriture du rapport sur le disque
        writeReport(content.toString());
    }

    /**
     * Enrichit les propriétés à vérifier depuis la ligne de commande.
     */
    private void enrichPropertiesFromSystem() {
        if (propertiesToCheck == null) {
            propertiesToCheck = new ArrayList<>();
        }
        String sysProp = System.getProperty("propertiesToCheck");
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            propertiesToCheck.addAll(Arrays.asList(sysProp.split(",")));
        } else {
            log.warn("⚠️ Aucune propriété à vérifier n'a été fournie via -DpropertiesToCheck.");
        }
    }

    /**
     * Charge dynamiquement les checkers via SPI et les initialise.
     */
    private void initCheckers() {
        ServiceLoader<CustomChecker> serviceLoader = ServiceLoader.load(CustomChecker.class);
        for (CustomChecker checker : serviceLoader) {
            try {
                initializeChecker(checker);
                checkers.add(checker);
            } catch (Exception e) {
                log.error("❌ Erreur d'initialisation du checker : " + checker.getClass().getName(), e);
            }
        }
    }

    /**
     * Initialise un checker en fonction de ses capacités.
     */
    private void initializeChecker(CustomChecker checker) {
        if (checker instanceof BasicInitializableChecker) {
            ((BasicInitializableChecker) checker).init(log, resolveRenderer());
        }
        if (checker instanceof InitializableChecker) {
            ((InitializableChecker) checker).init(log, repoSystem, repoSession, remoteRepositories, resolveRenderer());
        }
    }

    /**
     * Affiche la liste des checkers disponibles et éventuellement ceux sélectionnés.
     */
    private void logAvailableCheckers() {
        List<String> checkerIds = checkers.stream()
                .map(CustomChecker::getId)
                .sorted()
                .collect(Collectors.toList());

        log.info("📦 Checkers disponibles : " + checkerIds);

        if (checkersToRun != null) {
            List<String> invalidCheckers = checkersToRun.stream()
                    .filter(checkerId -> !checkerIds.contains(checkerId))
                    .collect(Collectors.toList());

            if (!invalidCheckers.isEmpty()) {
                log.warn("❌ Checkers non valides spécifiés : " + String.join(", ", invalidCheckers));
            }
        }

        if (!runAll) {
            log.info("✅ Checkers sélectionnés : " + checkersToRun);
        }
    }

    /**
     * Génère le rapport pour un module donné.
     * @param module le module Maven à analyser
     * @param renderer le moteur de rendu utilisé
     * @return contenu du rapport
     */
    private StringBuilder generateReportContent(MavenProject module, ReportRenderer renderer) {
        CheckerContext context = new CheckerContext(module, project, propertiesToCheck);
        StringBuilder content = new StringBuilder();
        content.append(renderer.renderHeader2("Module : " + module.getArtifactId()));

        for (CustomChecker checker : checkers) {
            String report = generateCheckerReport(checker, context);
            if (!report.isEmpty()) {
                content.append(report).append("\n");
            }
        }

        return content;
    }

    /**
     * Génère le rapport de tous les modules.
     * @param renderer le moteur de rendu utilisé
     * @param content le contenu du rapport
     */
    private void generateModuleReports(ReportRenderer renderer, StringBuilder content) {
        List<MavenProject> modules = project.getCollectedProjects();
        if (modules != null) {
            for (MavenProject module : modules) {
                content.append(generateReportContent(module, renderer));
            }
        }
    }

    /**
     * Exécute un checker si applicable et retourne son rapport.
     */
    private String generateCheckerReport(CustomChecker checker, CheckerContext context) {
        boolean shouldRun = runAll || (checkersToRun != null && checkersToRun.contains(checker.getId()));
        if (!shouldRun) return "";

        // Sauter les checkers non applicables
        List<String> topLevelOnlyCheckers = Arrays.asList("expectedModules", "propertyPresence");
        boolean isTopLevelOnly = topLevelOnlyCheckers.contains(checker.getId()) && !isTopLevelProject(context.getCurrentModule());
        boolean skipIfNotApi = "interfaceConformity".equals(checker.getId())
                && !context.getCurrentModule().getArtifactId().endsWith("-api");

        if (isTopLevelOnly || skipIfNotApi) {
            return ""; // Skip checker if not applicable
        }

        return checker.generateReport(context);
    }

    /**
     * Écrit le rapport généré sur disque.
     * @param content contenu HTML/Markdown/Text du rapport
     * @throws MojoExecutionException en cas d'erreur de fichier
     */
    private void writeReport(String content) throws MojoExecutionException {
        String ext = format != null && !format.isEmpty() ? format.get(0).toLowerCase() : "md";
        if (ext.equals("markdown")) ext = "md";

        File outputFile = new File(project.getBasedir(), "module-check-report." + ext);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            switch (ext) {
                case "md":
                    writer.write("# Rapport de Vérification\n\n");
                    writer.write(content);
                    break;
                case "text":
                    writer.write(content);
                    break;
                case "html":
                    String css = readResourceAsString();
                    writer.write("<html><head><meta charset=\"UTF-8\">\n");
                    writer.write("<title>Rapport de Vérification</title>\n");
                    writer.write("<style>" + css + "</style></head><body>\n");
                    writer.write("<h1>Rapport de Vérification</h1>\n");
                    writer.write(content);
                    writer.write("</body></html>");
                    break;
            }
        } catch (IOException e) {
            throw new MojoExecutionException("❌ Erreur lors de la création du fichier de rapport", e);
        }

        log.info("📄 Rapport généré : " + outputFile.getAbsolutePath());
        log.info("👉 file://" + outputFile.getAbsolutePath());
    }

    /**
     * Lit le contenu d'une ressource dans le classpath.
     *
     * @return contenu de la ressource
     * @throws IOException si la ressource est introuvable ou non lisible
     */
    private String readResourceAsString() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("assets/css/style.css")) {
            if (is == null) {
                throw new FileNotFoundException("Le fichier de ressource '" + "assets/css/style.css" + "' est introuvable dans le classpath.");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Vérifie si le projet courant est un pom parent.
     */
    private boolean isParentPom() {
        return project.getModules() != null && !project.getModules().isEmpty();
    }

    /**
     * Vérifie si le module passé est le projet racine.
     */
    private boolean isTopLevelProject(MavenProject module) {
        return module.getArtifactId().equals(project.getArtifactId());
    }

    /**
     * Résout dynamiquement le moteur de rendu à utiliser.
     */
    ReportRenderer resolveRenderer() {
        String firstFormat = format != null && !format.isEmpty() ? format.get(0).toLowerCase() : "markdown";
        String f = firstFormat.toLowerCase();

        switch (f) {
            case "html":
                return new HtmlReportRenderer();
            case "text":
                return new TextReportRenderer();
            case "markdown":
            case "md":
                return new MarkdownReportRenderer();
        }

        log.warn("Format inconnu '" + firstFormat + "', utilisation de Markdown par défaut.");
        return new MarkdownReportRenderer();
    }
}