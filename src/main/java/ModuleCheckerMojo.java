
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.elitost.maven.plugin.checker.checkers.*;
import org.elitost.maven.plugin.checker.renderers.HtmlReportRenderer;
import org.elitost.maven.plugin.checker.renderers.MarkdownReportRenderer;
import org.elitost.maven.plugin.checker.renderers.ReportRenderer;
import org.elitost.maven.plugin.checker.renderers.TextReportRenderer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

@Mojo(name = "check-modules", defaultPhase = LifecyclePhase.NONE)
@Execute(goal = "check-modules")
public class ModuleCheckerMojo extends AbstractMojo {

    @Parameter(property = "format", defaultValue = "html")
    private List<String> format;

    @Parameter(property = "checkersToRun")
    private List<String> checkersToRun;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepositories;

    @Parameter(defaultValue = "${project}")
    private MavenProject project;

    @Parameter(required = true)
    private List<String> propertiesToCheck;

    private ModuleChecker moduleChecker;
    private ParentVersionChecker parentChecker;
    private PropertyChecker propertyChecker;
    private HardcodedVersionChecker hardcodedChecker;
    private DependencyUpdateChecker updateChecker;
    private CommentedTagsChecker commentedTagsChecker;
    private RedundantPropertiesChecker redundantChecker;
private UnusedDependenciesChecker unusedDependenciesChecker;

    private Log log;
    private boolean runAll;

    @Override
    public void execute() throws MojoExecutionException {
        this.log = getLog();

        if (!isParentPom()) {
            log.info("🔍 Ce n'est pas le pom parent, le plugin ne s'exécute pas ici.");
            return;
        }

        initCheckers();

        // Initialisation du renderer
        ReportRenderer renderer = resolveRenderer();

        runAll = checkersToRun == null || checkersToRun.isEmpty();

        enrichPropertiesFromSystem();
        logSelectedCheckers();

        String content = generateReportContent(project, renderer);

        List<MavenProject> modules = project.getCollectedProjects();
        if (modules != null) {
            for (MavenProject module : modules) {
                content += generateReportContent(module, renderer);
            }
        }

        writeReport(content);
    }

    private void initCheckers() {
        moduleChecker = new ModuleChecker(log, resolveRenderer());
        parentChecker = new ParentVersionChecker(log, repoSystem, repoSession, remoteRepositories,resolveRenderer());
        propertyChecker = new PropertyChecker(log,resolveRenderer());
        hardcodedChecker = new HardcodedVersionChecker(log,resolveRenderer());
        updateChecker = new DependencyUpdateChecker(log, repoSystem, repoSession, remoteRepositories, resolveRenderer());
        commentedTagsChecker = new CommentedTagsChecker(log, resolveRenderer());
        redundantChecker = new RedundantPropertiesChecker(log, resolveRenderer());
        unusedDependenciesChecker = new UnusedDependenciesChecker(log, resolveRenderer());
    }

    private void enrichPropertiesFromSystem() {
        String sysProp = System.getProperty("propertiesToCheck");
        if (sysProp != null && !sysProp.isEmpty()) {
            propertiesToCheck.addAll(Arrays.asList(sysProp.split(",")));
        } else {
            log.warn("⚠️ Aucune propriété à vérifier n'a été fournie via -DpropertiesToCheck.");
        }
    }

    private void logSelectedCheckers() {
        if (!runAll) {
            log.info("✅ Checkers explicitement demandés : " + String.join(", ", checkersToRun));
        }
    }

    private String generateReportContent(MavenProject module, ReportRenderer renderer) {
        StringBuilder content = new StringBuilder();
        content.append(renderer.renderTitle("Module : " + module.getArtifactId()));

        if (isTopLevelProject(module) && (runAll || checkersToRun.contains("module"))) {
            content.append(moduleChecker.generateModuleCheckReport(module)).append("\n");
        }

        if (runAll || checkersToRun.contains("parent")) {
            content.append(parentChecker.generateParentVersionReport(module)).append("\n");
        }

        if (isTopLevelProject(module) && (runAll || checkersToRun.contains("property"))) {
            content.append(propertyChecker.generatePropertiesCheckReport(module, propertiesToCheck)).append("\n");
        }

        runCommonCheckers(module, renderer, content);

        return content.toString();
    }

    private void runCommonCheckers(MavenProject module, ReportRenderer renderer, StringBuilder content) {
        if (runAll || checkersToRun.contains("hardcoded")) {
            content.append(hardcodedChecker.generateHardcodedVersionReport(module)).append("\n");
        }

        if (runAll || checkersToRun.contains("outdated")) {
            content.append(updateChecker.generateOutdatedDependenciesReport(module.getOriginalModel().getDependencies()));
        }

        if (runAll || checkersToRun.contains("commented")) {
            content.append(commentedTagsChecker.generateCommentedTagsReport(module));
        }

        if (runAll || checkersToRun.contains("redundant")) {
            content.append(redundantChecker.generateRedundantPropertiesReport(module)).append("\n");
        }

        if (runAll || checkersToRun.contains("usage")) {
            content.append(unusedDependenciesChecker.generateReport(module)).append("\n");
        }
    }

    private void writeReport(String content) throws MojoExecutionException {
        String ext = format != null && !format.isEmpty() ? format.get(0).toLowerCase() : "md";

        if (ext.equals("markdown")) ext = "md";
        if (ext.equals("html")) ext = "html";

        File file = new File(project.getBasedir(), "module-check-report." + ext);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            if (ext.equals("md")) {
                writer.write("# Rapport de Vérification des Modules\n\n");
                writer.write(content);
            } else if (ext.equals("text")) {
                writer.write(content);
            } else if (ext.equals("html")) {
                writer.write("<html>\n<head>\n<title>Rapport de Vérification des Modules</title>\n");
                writer.write("<style>\n");
                writer.write("/* Global Styles */\n");
                writer.write("body { font-family: Arial, sans-serif; color: #333; margin: 20px; line-height: 1.6; }\n");
                writer.write("h1 { font-size: 28px; color: #2a3d66; border-bottom: 2px solid #2a3d66; padding-bottom: 10px; margin-bottom: 20px; }\n");
                writer.write("h2 { font-size: 24px; color: #3a5274; margin-top: 20px; border-bottom: 1px solid #ccc; padding-bottom: 5px; }\n");
                writer.write("h3 { font-size: 20px; color: #4a6a92; }\n");
                writer.write("pre { background-color: #f5f5f5; padding: 10px; border-radius: 5px; font-size: 14px; white-space: pre-wrap; word-wrap: break-word; }\n");
                writer.write("ul { list-style: none; padding-left: 0; }\n");
                writer.write("ul li { padding: 8px; border-bottom: 1px solid #e3e3e3; }\n");
                writer.write("table { width: 100%; border-collapse: collapse; margin-top: 20px; }\n");
                writer.write("table th, table td { padding: 12px; text-align: left; border: 1px solid #e3e3e3; }\n");
                writer.write("table th { background-color: #f2f2f2; font-weight: bold; }\n");
                writer.write("section { margin-top: 20px; padding: 20px; background-color: #f9f9f9; border-radius: 8px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1); }\n");
                writer.write("section h3 { margin-top: 0; }\n");
                writer.write("footer { margin-top: 40px; padding-top: 20px; text-align: center; font-size: 14px; color: #777; }\n");
                writer.write("</style>\n</head>\n<body>\n");
                writer.write("<h1>Rapport de Vérification des Modules</h1>\n");
                writer.write(content);
                writer.write("</body>\n</html>");
            }

        } catch (IOException e) {
            throw new MojoExecutionException("❌ Erreur lors de la création du fichier de rapport", e);
        }

        log.info("📄 Rapport global généré : " + file.getAbsolutePath());
        log.info("Vous pouvez consulter le rapport ici : file://" + file.getAbsolutePath());
    }

    private boolean isParentPom() {
        return project.getModules() != null && !project.getModules().isEmpty();
    }

    private boolean isTopLevelProject(MavenProject module) {
        return module.getArtifactId().equals(project.getArtifactId());
    }

    ReportRenderer resolveRenderer() {
        String firstFormat = format != null && !format.isEmpty() ? format.get(0) : "markdown";
        String lowerFormat = firstFormat.toLowerCase();

        ReportRenderer renderer;
        switch (lowerFormat) {
            case "html":
                renderer = new HtmlReportRenderer();
                break;
            case "text":
                renderer = new TextReportRenderer();
                break;
            case "markdown":
                renderer = new MarkdownReportRenderer();
                break;
            default:
                log.warn("Format inconnu '" + firstFormat + "', utilisation de Markdown par défaut.");
                renderer = new MarkdownReportRenderer();
                break;
        }
        return renderer;
    }
}