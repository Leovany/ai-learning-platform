package com.ailearning.platform.document.dto;

import com.ailearning.platform.document.DocumentStatus;
import com.ailearning.platform.document.LearningDocument;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DocumentVO {

    private Long id;
    private String fileName;
    private Long fileSize;
    private Integer pageCount;
    private DocumentStatus status;
    private LocalDateTime createdAt;
    private String extractedText;
    private String textPreview;

    public static DocumentVO from(LearningDocument doc, boolean includeFullText) {
        String text = doc.getExtractedText();
        String preview = null;
        if (text != null && !text.isBlank()) {
            preview = text.length() > 200 ? text.substring(0, 200) + "…" : text;
        }
        return DocumentVO.builder()
                .id(doc.getId())
                .fileName(doc.getFileName())
                .fileSize(doc.getFileSize())
                .pageCount(doc.getPageCount())
                .status(doc.getStatus())
                .createdAt(doc.getCreatedAt())
                .extractedText(includeFullText ? text : null)
                .textPreview(preview)
                .build();
    }

    public static DocumentVO summary(LearningDocument doc) {
        return from(doc, false);
    }

    public static DocumentVO detail(LearningDocument doc) {
        return from(doc, true);
    }
}
