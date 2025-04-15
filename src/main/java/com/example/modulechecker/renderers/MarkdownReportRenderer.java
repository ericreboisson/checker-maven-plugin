// === Implémentation Markdown ===
package com.example.modulechecker.renderers;

public class MarkdownReportRenderer implements ReportRenderer {
    @Override
    public String renderTitle(String title) {
        return "### " + title + "\n\n";
    }

    @Override
    public String renderParagraph(String text) {
        return text + "\n\n";
    }

    @Override
    public String renderTable(String[] headers, String[][] rows) {
        StringBuilder sb = new StringBuilder();
        for (String header : headers) {
            sb.append("| ").append(header).append(" ");
        }
        sb.append("|\n");
        sb.append("|").append("---|".repeat(headers.length)).append("\n");
        for (String[] row : rows) {
            for (String col : row) {
                sb.append("| ").append(col).append(" ");
            }
            sb.append("|\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    @Override
    public String renderWarning(String text) {
        return "> ⚠️ " + text + "\n\n";
    }

    @Override
    public String renderInfo(String text) {
        return "> ℹ️ " + text + "\n\n";
    }

    @Override
    public String renderError(String text) {
        return "> ❌ " + text + "\n\n";
    }
}