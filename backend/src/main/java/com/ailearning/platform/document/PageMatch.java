package com.ailearning.platform.document;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PageMatch {

    private int page;
    private String matchedSnippet;
    private int highlightStart;
    private int highlightEnd;

    public boolean hasHighlight() {
        return highlightStart >= 0 && highlightEnd > highlightStart;
    }
}
