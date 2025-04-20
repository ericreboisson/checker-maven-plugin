package org.elitost.maven.plugins.renderers;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class PdfReportRenderer implements ReportRenderer {

    private final Document document;
    private final PdfWriter writer;
    private final ByteArrayOutputStream output;
    private final Font h1 = new Font(Font.HELVETICA, 18, Font.BOLD);
    private final Font h2 = new Font(Font.HELVETICA, 14, Font.BOLD);
    private final Font h3 = new Font(Font.HELVETICA, 12, Font.BOLD);
    private final Font normal = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private final Font error = new Font(Font.HELVETICA, 10, Font.BOLD, Color.RED);
    private final Font info = new Font(Font.HELVETICA, 10, Font.ITALIC, Color.BLUE);
    private final Font warning = new Font(Font.HELVETICA, 10, Font.BOLD, Color.ORANGE);

    public PdfReportRenderer(String outputPath) throws Exception {
        output = new ByteArrayOutputStream();
        document = new Document();
        writer = PdfWriter.getInstance(document, output);
        document.open();
    }

    public void saveTo(String outputFilePath) throws Exception {
        document.close();
        try (OutputStream out = new FileOutputStream(outputFilePath)) {
            output.writeTo(out);
        }
    }

    @Override
    public String renderHeader1(String title) {
        document.add(new Paragraph(title, h1));
        return "";
    }

    @Override
    public String renderHeader2(String title) {
        document.add(new Paragraph(title, h2));
        return "";
    }

    @Override
    public String renderHeader3(String title) {
        document.add(new Paragraph(title, h3));
        return "";
    }

    @Override
    public String renderParagraph(String text) {
        document.add(new Paragraph(text, normal));
        return "";
    }

    @Override
    public String renderTable(String[] headers, String[][] rows) {
        PdfPTable table = new PdfPTable(headers.length);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, h3));
            cell.setBackgroundColor(Color.LIGHT_GRAY);
            table.addCell(cell);
        }
        for (String[] row : rows) {
            for (String cell : row) {
                table.addCell(new Phrase(cell != null ? cell : "", normal));
            }
        }
        document.add(table);
        return "";
    }

    @Override
    public String renderWarning(String text) {
        document.add(new Paragraph("⚠️ " + text, warning));
        return "";
    }

    @Override
    public String renderInfo(String text) {
        document.add(new Paragraph("ℹ️ " + text, info));
        return "";
    }

    @Override
    public String renderError(String text) {
        document.add(new Paragraph("❌ " + text, error));
        return "";
    }

    @Override
    public String openIndentedSection() {
        document.add(new Paragraph("")); // espace
        return "";
    }

    @Override
    public String closeIndentedSection() {
        document.add(new Paragraph(""));
        return "";
    }
}