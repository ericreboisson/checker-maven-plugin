package com.example.modulechecker.checkers;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.StringWriter;
import java.io.PrintWriter;

public class PomSchemaValidatorChecker {

    private final Log log;
    private static final String XSD_URL = "https://maven.apache.org/xsd/maven-4.0.0.xsd";

    public PomSchemaValidatorChecker(Log log) {
        this.log = log;
    }

    public String generatePomSchemaValidationReport(MavenProject project) {
        StringBuilder report = new StringBuilder();

        File pomFile = project.getFile();
        if (pomFile == null || !pomFile.exists()) {
            return "⚠️ Aucun `pom.xml` trouvé pour ce module.\n";
        }

        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Validator validator = factory.newSchema(new StreamSource(XSD_URL)).newValidator();

            validator.validate(new StreamSource(pomFile));
            // Pas d'erreur = POM conforme
            return ""; // Ne rien afficher si tout va bien
        } catch (SAXException e) {
            // En cas d’erreur de validation
            report.append("❌ **Erreur de validation du `pom.xml` selon le XSD officiel Maven**\n\n");
            report.append("```\n").append(e.getMessage()).append("\n```\n");
        } catch (Exception e) {
            // Autres erreurs (connexion, I/O, etc.)
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            report.append("❌ **Erreur inattendue lors de la validation du `pom.xml`**\n\n");
            report.append("```\n").append(sw.toString()).append("\n```\n");
        }

        return report.toString();
    }
}