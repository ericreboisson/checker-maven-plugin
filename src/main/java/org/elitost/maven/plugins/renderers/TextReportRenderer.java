package org.elitost.maven.plugins.renderers;

/**
 * Renderer simple pour les rapports en texte brut (ex. console ou .txt).
 */
public class TextReportRenderer implements ReportRenderer {

    @Override
    public String renderHeader1(String title) {
        return "\n=== " + title + " ===\n\n";
    }

    @Override
    public String renderHeader2(String title) {
        return "\n=== " + title + " ===\n\n";
    }

    @Override
    public String renderHeader3(String title) {
        return "\n=== " + title + " ===\n\n";
    }


    @Override
    public String renderParagraph(String text) {
        return text + "\n\n";
    }

    @Override
    public String renderTable(String[] headers, String[][] rows) {
        StringBuilder sb = new StringBuilder();

        // Calcule la largeur maximale de chaque colonne
        int[] colWidths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            colWidths[i] = headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                colWidths[i] = Math.max(colWidths[i], row[i].length());
            }
        }

        // Ligne d'en-tête
        for (int i = 0; i < headers.length; i++) {
            sb.append(pad(headers[i], colWidths[i])).append(" | ");
        }
        sb.append("\n");

        // Séparateur
        for (int colWidth : colWidths) {
            sb.append("-".repeat(colWidth)).append(" | ");
        }
        sb.append("\n");

        // Lignes de données
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                sb.append(pad(row[i], colWidths[i])).append(" | ");
            }
            sb.append("\n");
        }

        sb.append("\n");
        return sb.toString();
    }

    @Override
    public String renderWarning(String text) {
        return "⚠️ AVERTISSEMENT : " + text + "\n";
    }

    @Override
    public String renderInfo(String text) {
        return "ℹ️ INFO : " + text + "\n";
    }

    @Override
    public String renderError(String text) {
        return "❌ ERREUR : " + text + "\n";
    }

    @Override
    public String renderAnchor(String id) {
        // En mode texte, une ancre peut juste être un marqueur visuel
        return "[[" + id + "]]\n";
    }

    private String pad(String text, int width) {
        return String.format("%-" + width + "s", text);
    }
}