package com.example.modulechecker;

import com.example.modulechecker.checkers.*;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Mojo(name = "check-modules", defaultPhase = LifecyclePhase.NONE)
@Execute(goal = "check-modules")
public class ModuleCheckerMojo extends AbstractMojo {

    @Parameter(property = "mavenExecutable", defaultValue = "mvn")
    private String mavenExecutable;

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

    @Override
    public void execute() throws MojoExecutionException {
        if (!isParentPom()) {
            getLog().info("🔍 Ce n'est pas le pom parent, le plugin ne s'exécute pas ici.");
            return;
        }

        Log log = getLog();
        StringBuilder markdownContent = new StringBuilder("# Rapport de Vérification des Modules\n\n");
        // Récupérer la liste des propriétés passées en ligne de commande via -D
        String propertiesParam = System.getProperty("propertiesToCheck");
        if (propertiesParam != null && !propertiesParam.isEmpty()) {
            propertiesToCheck.addAll(Arrays.asList(propertiesParam.split(",")));
        } else {
            log.warn("⚠️ Aucune propriété à vérifier n'a été fournie via -DpropertiesToCheck.");
        }

        // 🗂️ Chemin du fichier rapport à la racine du projet parent
        File reportFile = new File(project.getBasedir(), "module-check-report.md");

        // Instanciation des checkers
        ModuleChecker moduleChecker = new ModuleChecker(log);
        PropertyChecker propertyChecker = new PropertyChecker(log);
        HardcodedVersionChecker hardcodedChecker = new HardcodedVersionChecker(log);
        DependencyUpdateChecker updateChecker = new DependencyUpdateChecker(log, repoSystem, repoSession, remoteRepositories);
        CommentedTagsChecker commentedTagsChecker = new CommentedTagsChecker(log);
        PomSchemaValidatorChecker schemaChecker = new PomSchemaValidatorChecker(log);
        RedundantPropertiesChecker redundantChecker = new RedundantPropertiesChecker(log);
        UnusedDependenciesChecker unusedChecker = new UnusedDependenciesChecker(log);

// ...
        // 🔧 Ajout du rapport pour le POM parent
        markdownContent.append("## Module Parent : ").append(project.getArtifactId()).append("\n");
        markdownContent.append(moduleChecker.generateModuleCheckReport(project)).append("\n");;
        markdownContent.append(propertyChecker.generatePropertiesCheckReport(project, propertiesToCheck)).append("\n");;
        markdownContent.append(hardcodedChecker.generateHardcodedVersionReport(project)).append("\n");;
        // Vérification des dépendances obsolètes pour le module parent
        markdownContent.append(updateChecker.generateOutdatedDependenciesReport(project.getOriginalModel().getDependencies()));
        markdownContent.append(commentedTagsChecker.generateCommentedTagsReport(project));
        markdownContent.append(schemaChecker.generatePomSchemaValidationReport(project)).append("\n");
        markdownContent.append(redundantChecker.generateRedundantPropertiesReport(project)).append("\n");
        markdownContent.append(unusedChecker.generateUnusedDependenciesReport(project, mavenExecutable));

        markdownContent.append("\n---\n");

        // 🔁 Ajout des rapports pour les modules enfants
        if (project.getCollectedProjects() != null) {
            for (Object moduleObj : project.getCollectedProjects()) {
                if (moduleObj instanceof MavenProject module) {
                    markdownContent.append("## Module : ").append(module.getArtifactId()).append("\n");
                    markdownContent.append(hardcodedChecker.generateHardcodedVersionReport(module)).append("\n");;
                    // Vérification des dépendances obsolètes pour le module parent
                    markdownContent.append(updateChecker.generateOutdatedDependenciesReport(module.getOriginalModel().getDependencies()));
                    markdownContent.append(commentedTagsChecker.generateCommentedTagsReport(module));
                    markdownContent.append(schemaChecker.generatePomSchemaValidationReport(module)).append("\n");
                    markdownContent.append(redundantChecker.generateRedundantPropertiesReport(module)).append("\n");
                    markdownContent.append(unusedChecker.generateUnusedDependenciesReport(module, mavenExecutable));

                    markdownContent.append("\n---\n");
                }
            }
        }

        // ✍️ Création du fichier Markdown
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportFile))) {
            writer.write(markdownContent.toString());
        } catch (IOException e) {
            throw new MojoExecutionException("❌ Erreur lors de la création du fichier de rapport Markdown", e);
        }

        // 🔗 Lien vers le rapport
        log.info("📄 Rapport global généré : " + reportFile.getAbsolutePath());
        log.info("Vous pouvez consulter le rapport ici : file://" + reportFile.getAbsolutePath());
    }

    /**
     * Vérifie si le POM actuel est celui du parent.
     */
    private boolean isParentPom() {
        return project.getModules() != null && !project.getModules().isEmpty();
    }
}