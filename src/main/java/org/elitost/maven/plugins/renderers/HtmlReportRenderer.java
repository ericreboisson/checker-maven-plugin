package org.elitost.maven.plugins.renderers;

public class HtmlReportRenderer implements ReportRenderer {

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    @Override
    public String renderHeader1(String title) {
        return "<h1>" + escapeHtml(title) + "</h1>\n";
    }

    @Override
    public String renderHeader2(String title) {
        return "<h2>" + escapeHtml(title) + "</h2>\n";
    }

    @Override
    public String renderHeader3(String title) {
        return "<h3>" + escapeHtml(title) + "</h3>\n";
    }

    @Override
    public String renderParagraph(String text) {
        return "<p>" + escapeHtml(text) + "</p>\n";
    }

    @Override
    public String renderTable(String[] headers, String[][] rows) {
        StringBuilder sb = new StringBuilder("<table border='1'>\n<thead><tr>");
        for (String header : headers) {
            sb.append("<th>").append(escapeHtml(header)).append("</th>");
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
        return "<div class='warning'>⚠️ " + escapeHtml(text) + "</div>\n";
    }

    @Override
    public String renderInfo(String text) {
        return "<div class='info'>ℹ️ " + escapeHtml(text) + "</div>\n";
    }

    @Override
    public String renderError(String text) {
        return "<div class='error'>❌ " + escapeHtml(text) + "</div>\n";
    }

    @Override
    public String renderAnchor(String id) {
        return "<a name=\"" + escapeHtml(id) + "\"></a>\n";
    }

    public String openIndentedSection() {
        return "<div class='indented-section'>\n";
    }

    public String closeIndentedSection() {
        return "</div>\n";
    }
}