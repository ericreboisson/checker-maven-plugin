package org.elitost.maven.plugins;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "help")
public class HelpMojo extends AbstractMojo {

    @Parameter(defaultValue = "false", property = "detail")
    private boolean detail;

    public void execute() throws MojoExecutionException {
        getLog().info("Checker Maven Plugin - Aide");
        getLog().info("-------------------------------------");
        getLog().info("Ce plugin permet d'exécuter plusieurs vérifications sur des projets Maven multi-modules.");
        getLog().info("Usage : mvn org.elitost.maven.plugins:checker-maven-plugin:check [-Dformat=markdown|html|text] [-DcheckersToRun=...]");
        getLog().info("");
        getLog().info("Paramètres :");
        getLog().info("  -Dformat=xxx               Format de sortie (markdown, html, text). Défaut : html");
        getLog().info("  -DcheckersToRun=...        Liste des checkers à exécuter (séparés par virgule), ex: module, parent, property, hardcoded, outdated, commented, redundant, usage");
        getLog().info("  -DpropertiesToCheck=...    Liste des propriétés à vérifier pour le checker 'property'");
        getLog().info("");

        if (detail) {
            getLog().info("Liste des checkers disponibles :");
            getLog().info(" - module       : Vérifie les infos générales du module");
            getLog().info(" - parent       : Vérifie la version du parent");
            getLog().info(" - property     : Vérifie les propriétés demandées");
            getLog().info(" - hardcoded    : Détecte les versions codées en dur");
            getLog().info(" - outdated     : Vérifie les dépendances obsolètes");
            getLog().info(" - commented    : Détecte les balises XML commentées");
            getLog().info(" - redundant    : Détecte les propriétés redondantes");
            getLog().info(" - usage        : Détecte les dépendances non utilisées");
        }
    }
}