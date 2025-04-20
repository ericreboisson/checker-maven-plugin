package org.elitost.maven.plugins.renderers;

public interface ReportRenderer {
    String renderHeader1(String title);

    String renderHeader2(String title);

    String renderHeader3(String title);

    String renderParagraph(String text);

    String renderTable(String[] headers, String[][] rows);

    String renderWarning(String text);

    String renderInfo(String text);

    String renderError(String text);

    String openIndentedSection();

    String closeIndentedSection();
}
