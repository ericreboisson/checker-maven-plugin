package org.elitost.maven.plugins.utils;

public class HtmlUtils {

    /**
     * Échappe les caractères spéciaux HTML.
     * Ajout d'une gestion des espaces pour un rendu plus propre.
     */
    public static String escapeHtml(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\n", "<br/>")
                .replace(" ", "&nbsp;");
    }
}
