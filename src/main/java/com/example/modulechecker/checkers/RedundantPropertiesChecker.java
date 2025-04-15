package com.example.modulechecker.checkers;

import com.example.modulechecker.renderers.ReportRenderer;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * Analyseur de propriétés redondantes dans un fichier pom.xml :
 * - détecte les propriétés définies dans un module mais jamais utilisées dans aucun pom du projet.
 */
public class RedundantPropertiesChecker {

    private final Log log;
    private final ReportRenderer renderer;

    public RedundantPropertiesChecker(Log log, ReportRenderer renderer) {
        this.log = log;
        this.renderer = renderer;
    }

    /**
     * Génère un rapport des propriétés définies dans le module mais jamais utilisées dans l'ensemble du projet.
     *
     * @param project Le projet Maven (racine ou module) à analyser.
     * @return Rapport rendu via le renderer.
     */
    public String generateRedundantPropertiesReport(MavenProject project) {
        String artifactId = project.getArtifactId();
        Model model = project.getOriginalModel();
        Properties propertiesDefinedInThisModule = model.getProperties();

        // Si aucune propriété définie, rien à signaler
        if (propertiesDefinedInThisModule.isEmpty()) {
            return "";
        }

        // 📁 Récupération de tous les fichiers pom.xml du projet (y compris enfants)
        List<File> allPomFiles = new ArrayList<>();
        collectPomFilesRecursively(project, allPomFiles);

        // 📄 Lecture de tous les contenus des pom.xml (concaténés)
        StringBuilder allPomContents = new StringBuilder();
        for (File pomFile : allPomFiles) {
            try {
                allPomContents.append(Files.readString(pomFile.toPath())).append("\n");
            } catch (Exception e) {
                log.warn("⚠️ [RedundantPropertiesChecker] Erreur de lecture : " + pomFile.getAbsolutePath(), e);
            }
        }

        // 🔍 Vérification des propriétés non référencées dans aucun fichier
        List<String[]> unusedPropertiesRows = new ArrayList<>();
        for (String propertyName : propertiesDefinedInThisModule.stringPropertyNames()) {
            String referenceSyntax = "${" + propertyName.trim() + "}";

            if (!allPomContents.toString().contains(referenceSyntax)) {
                log.warn("⚠️ [RedundantPropertiesChecker] Propriété non utilisée détectée : " + propertyName + " (module : " + artifactId + ")");
                unusedPropertiesRows.add(new String[] { "`" + propertyName + "`" });
            }
        }

        // ✅ Si toutes les propriétés sont utilisées, on ne retourne rien
        if (unusedPropertiesRows.isEmpty()) {
            return "";
        }

        // 📝 Génération du rapport Markdown
        StringBuilder report = new StringBuilder();
        report.append(renderer.renderTitle("🧹 Propriétés Redondantes dans `" + artifactId + "`"));
        report.append(renderer.renderParagraph(
            "Les propriétés suivantes sont définies dans ce module mais ne sont utilisées dans aucun `pom.xml`" +
            "du projet (ni parent, ni enfants)."
        ));
        report.append(renderer.renderTable(
            new String[] { "Nom de la propriété" },
            unusedPropertiesRows.toArray(new String[0][])
        ));

        return report.toString();
    }

    /**
     * Récupère récursivement tous les fichiers pom.xml du projet et de ses sous-modules.
     *
     * @param project   Projet Maven (racine ou module)
     * @param pomFiles  Liste accumulative des fichiers pom.xml trouvés
     */
    private void collectPomFilesRecursively(MavenProject project, List<File> pomFiles) {
        File pomFile = project.getFile();
        if (pomFile != null && pomFile.exists()) {
            pomFiles.add(pomFile);
        }

        List<MavenProject> subModules = project.getCollectedProjects();

        if (subModules != null) {
            for (MavenProject subModule : subModules) {
                collectPomFilesRecursively(subModule, pomFiles);
            }
        }
    }
}