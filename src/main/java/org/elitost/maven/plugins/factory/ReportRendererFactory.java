package org.elitost.maven.plugins.factory;

import org.elitost.maven.plugins.renderers.*;

public class ReportRendererFactory {

    public static ReportRenderer createRenderer(String format) {
        if (format == null) {
            return new MarkdownReportRenderer();
        }

        switch (format.toLowerCase()) {
            case "html":
                return new HtmlReportRenderer();
            case "text":
                return new TextReportRenderer();
            case "markdown":
            case "md":
                return new MarkdownReportRenderer();
            default:
                return new MarkdownReportRenderer();
        }
    }
}