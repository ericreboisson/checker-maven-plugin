package com.example.modulechecker.renderers;

public interface ReportRenderer {
    String renderTitle(String title);
    String renderParagraph(String text);
    String renderTable(String[] headers, String[][] rows);
    String renderWarning(String text);
    String renderInfo(String text);
    String renderError(String text);
}
