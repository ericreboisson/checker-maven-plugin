// === Implémentation HTML ===
package org.elitost.maven.plugin.checker.renderers;

public class HtmlReportRenderer implements ReportRenderer {
    @Override
    public String renderTitle(String title) {
        return "<h3>" + title + "</h3>\n";
    }

    @Override
    public String renderParagraph(String text) {
        return "<p>" + text + "</p>\n";
    }

    @Override
    public String renderTable(String[] headers, String[][] rows) {
        StringBuilder sb = new StringBuilder("<table border='1'>\n<thead><tr>");
        for (String header : headers) {
            sb.append("<th>").append(header).append("</th>");
        }
        sb.append("</tr></thead><tbody>\n");
        for (String[] row : rows) {
            sb.append("<tr>");
            for (String col : row) {
                sb.append("<td>").append(col).append("</td>");
            }
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table>\n");
        return sb.toString();
    }

    @Override
    public String renderWarning(String text) {
        return "<div class='warning'>⚠️ " + text + "</div>\n";
    }

    @Override
    public String renderInfo(String text) {
        return "<div class='info'>ℹ️ " + text + "</div>\n";
    }

    @Override
    public String renderError(String text) {
        return "<div class='error'>❌ " + text + "</div>\n";
    }

    @Override
    public String renderAnchor(String id) {
        return "<a name=\"" + id + "\"></a>\n";
    }
}