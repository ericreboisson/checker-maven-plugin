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
            getLog().info(" - expectedModules          : Vérifie les infos générales du module");
            getLog().info(" - parentVersion            : Vérifie la version du parent");
            getLog().info(" - propertyPresence         : Vérifie les propriétés demandées");
            getLog().info(" - hardcodedVersion         : Détecte les versions codées en dur");
            getLog().info(" - outdatedDependencies     : Vérifie les dépendances obsolètes");
            getLog().info(" - commentedTags            : Détecte les balises XML commentées");
            getLog().info(" - redundantProperties      : Détecte les propriétés redondantes");
            getLog().info(" - unusedDependencies       : Détecte les dépendances non utilisées");
            getLog().info(" - dependenciesRedefinition : Détecte les versions de dépendances redéfinies");
            getLog().info(" - interfaceConformity      : check de la conformité des interfaces");
            getLog().info(" - urls                     : check de la présence d'URLs");
        }
    }
}