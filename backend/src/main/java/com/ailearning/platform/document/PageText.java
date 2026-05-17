package com.ailearning.platform.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageText {

    /** PDF 页码，从 1 开始 */
    private int page;
    private String text;
    /** 该页文本在全文中的起始字符偏移（含页间分隔） */
    private int startOffset;
    /** 该页文本在全文中的结束字符偏移（不含） */
    private int endOffset;
}
