package com.ailearning.platform.document;

import com.ailearning.platform.common.BusinessException;
import com.ailearning.platform.document.dto.DocumentPageVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentPageService {

    private final DocumentRepository documentRepository;
    private final PdfPageLocator pdfPageLocator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public DocumentPageVO getPage(Long documentId, int page, String highlightQuote) {
        LearningDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> BusinessException.notFound("文档不存在"));
        List<PageText> pages = loadPages(doc);
        if (page < 1 || (doc.getPageCount() != null && page > doc.getPageCount())) {
            throw BusinessException.badRequest("页码无效");
        }
        String text = pages.stream()
                .filter(p -> p.getPage() == page)
                .map(PageText::getText)
                .findFirst()
                .orElse("");

        DocumentPageVO.DocumentPageVOBuilder builder = DocumentPageVO.builder()
                .documentId(documentId)
                .fileName(doc.getFileName())
                .page(page)
                .pageCount(doc.getPageCount())
                .text(text);

        if (highlightQuote != null && !highlightQuote.isBlank() && !text.isEmpty()) {
            PageMatch match = pdfPageLocator.locateMatch(
                    pages.stream().filter(p -> p.getPage() == page).toList(),
                    highlightQuote,
                    null
            );
            if (match != null && match.hasHighlight()) {
                builder.highlightStart(match.getHighlightStart())
                        .highlightEnd(match.getHighlightEnd())
                        .highlightText(text.substring(match.getHighlightStart(), match.getHighlightEnd()));
            }
        }
        return builder.build();
    }

    public List<PageText> loadPages(LearningDocument doc) {
        if (doc.getPageTextsJson() == null || doc.getPageTextsJson().isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(doc.getPageTextsJson(), new TypeReference<List<PageText>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static String toJson(List<PageText> pages, ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(pages);
        } catch (Exception e) {
            return "[]";
        }
    }
}
