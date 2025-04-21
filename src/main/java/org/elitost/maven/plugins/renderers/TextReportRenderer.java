package org.elitost.maven.plugins.renderers;

/**
 * Renderer pour la génération de rapports en texte brut (console, fichiers .txt).
 * Fournit une mise en forme tabulaire simple avec alignement et indicateurs visuels.
 */
public class TextReportRenderer implements ReportRenderer {

    @Override
    public String renderHeader1(String title) {
        return "\n==================== " + title + " ====================\n\n";
    }

    @Override
    public String renderHeader2(String title) {
        return "\n-------------------- " + title + " --------------------\n\n";
    }

    @Override
    public String renderHeader3(String title) {
        return "\n>>> " + title.toUpperCase() + " <<<\n\n";
    }

    @Override
    public String renderParagraph(String text) {
        return text + "\n\n";
    }

    @Override
    public String renderTable(String[] headers, String[][] rows) {
        StringBuilder sb = new StringBuilder();

        // Calcule la largeur maximale de chaque colonne
        int[] colWidths = computeColumnWidths(headers, rows);

        // Ligne d'en-tête
        for (int i = 0; i < headers.length; i++) {
            sb.append("| ").append(pad(headers[i], colWidths[i])).append(" ");
        }
        sb.append("|\n");

        // Séparateur
        for (int width : colWidths) {
            sb.append("| ").append("-".repeat(width)).append(" ");
        }
        sb.append("|\n");

        // Lignes de données
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                sb.append("| ").append(pad(row[i], colWidths[i])).append(" ");
            }
            sb.append("|\n");
        }

        sb.append("\n");
        return sb.toString();
    }

    @Override
    public String renderWarning(String text) {
        return "⚠️  AVERTISSEMENT : " + text + "\n";
    }

    @Override
    public String renderInfo(String text) {
        return "ℹ️  INFO : " + text + "\n";
    }

    @Override
    public String renderError(String text) {
        return "❌ ERREUR : " + text + "\n";
    }

    @Override
    public String openIndentedSection() {
        return ""; // Optionnellement, tu pourrais préfixer chaque ligne avec 2 espaces
    }

    @Override
    public String closeIndentedSection() {
        return "";
    }

    // Calcule la largeur maximale de chaque colonne (headers + contenu)
    private int[] computeColumnWidths(String[] headers, String[][] rows) {
        int[] widths = new int[headers.length];

        for (int i = 0; i < headers.length; i++) {
            widths[i] = headers[i].length();
        }

        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i] != null ? row[i].length() : 0);
            }
        }

        return widths;
    }

    private String pad(String text, int width) {
        return String.format("%-" + width + "s", text != null ? text : "");
    }
}