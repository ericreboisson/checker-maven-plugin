package org.elitost.maven.plugins.renderers;

public class MarkdownReportRenderer implements ReportRenderer {

    @Override
    public String renderHeader1(String title) {
        return "# " + title + "\n\n";
    }

    @Override
    public String renderHeader2(String title) {
        return "## " + title + "\n\n";
    }

    @Override
    public String renderHeader3(String title) {
        return "### " + title.toUpperCase() + "\n\n"; // même intention que le H3 HTML stylisé
    }

    @Override
    public String renderParagraph(String text) {
        return text + "\n\n";
    }

    @Override
    public String renderTable(String[] headers, String[][] rows) {
        StringBuilder sb = new StringBuilder();

        // Header
        for (String header : headers) {
            sb.append("| ").append(header).append(" ");
        }
        sb.append("|\n");

        // Separator
        sb.append("|");
        for (int i = 0; i < headers.length; i++) {
            sb.append(" --- |");
        }
        sb.append("\n");

        // Rows
        for (String[] row : rows) {
            for (String cell : row) {
                sb.append("| ").append(cell).append(" ");
            }
            sb.append("|\n");
        }

        sb.append("\n");
        return sb.toString();
    }

    @Override
    public String renderWarning(String text) {
        return "> ⚠️ **AVERTISSEMENT :** " + text + "\n\n";
    }

    @Override
    public String renderInfo(String text) {
        return "> ℹ️ " + text + "\n\n";
    }

    @Override
    public String renderError(String text) {
        return "> ❌ **ERREUR :** " + text + "\n\n";
    }

    @Override
    public String openIndentedSection() {
        return "<!-- indented-section start -->\n\n";
    }

    @Override
    public String closeIndentedSection() {
        return "\n<!-- indented-section end -->\n\n";
    }
}