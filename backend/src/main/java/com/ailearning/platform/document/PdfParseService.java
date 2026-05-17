package com.ailearning.platform.document;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PdfParseService {

    private static final String PAGE_SEPARATOR = "\n\n";

    public PdfParseResult parse(Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            int pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            List<PageText> pages = new ArrayList<>();
            StringBuilder full = new StringBuilder();
            int offset = 0;

            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);
                String trimmed = pageText != null ? pageText.trim() : "";

                int startOffset = offset;
                if (!trimmed.isEmpty()) {
                    if (full.length() > 0) {
                        full.append(PAGE_SEPARATOR);
                        offset += PAGE_SEPARATOR.length();
                        startOffset = offset;
                    }
                    full.append(trimmed);
                    offset += trimmed.length();
                }
                pages.add(new PageText(page, trimmed, startOffset, offset));
            }

            log.debug("Parsed PDF {}: {} pages, {} chars", filePath.getFileName(), pageCount, full.length());
            return new PdfParseResult(full.toString(), pageCount, pages);
        }
    }

    public record PdfParseResult(String text, int pageCount, List<PageText> pages) {}
}
