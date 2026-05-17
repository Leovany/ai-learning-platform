package com.ailearning.platform.document.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentPageVO {

    private Long documentId;
    private String fileName;
    private Integer page;
    private Integer pageCount;
    private String text;
    /** 高亮起始（页内字符偏移） */
    private Integer highlightStart;
    /** 高亮结束（页内字符偏移） */
    private Integer highlightEnd;
    private String highlightText;
}
