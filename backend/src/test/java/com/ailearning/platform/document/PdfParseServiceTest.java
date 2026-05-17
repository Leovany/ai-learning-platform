package com.ailearning.platform.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PdfParseServiceTest {

  private final PdfParseService pdfParseService = new PdfParseService();

  @Test
  void parse_extractsTextAndPageCount(@TempDir Path tempDir) throws Exception {
    Path pdfPath = tempDir.resolve("sample.pdf");
    try (PDDocument doc = new PDDocument()) {
      PDPage page = new PDPage();
      doc.addPage(page);
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        cs.newLineAtOffset(100, 700);
        cs.showText("Hello PDF test content");
        cs.endText();
      }
      doc.save(pdfPath.toFile());
    }

    PdfParseService.PdfParseResult result = pdfParseService.parse(pdfPath);

    assertEquals(1, result.pageCount());
    assertTrue(result.text().contains("Hello PDF test content"));
    assertEquals(1, result.pages().size());
    assertTrue(result.pages().get(0).getText().contains("Hello PDF test content"));
    assertEquals(1, result.pages().get(0).getPage());
    assertEquals(0, result.pages().get(0).getStartOffset());
    assertTrue(result.pages().get(0).getEndOffset() > 0);
  }
}
