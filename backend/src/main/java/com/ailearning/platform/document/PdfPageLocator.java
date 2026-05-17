package com.ailearning.platform.document;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class PdfPageLocator {

    private static final int MIN_SNIPPET_LEN = 6;

    public PageMatch locateMatch(List<PageText> pages, String sourceQuote, String fallbackText) {
        if (pages == null || pages.isEmpty()) {
            return null;
        }
        String primary = pickNeedle(sourceQuote, fallbackText);
        PageMatch fromQuote = matchOnPages(pages, primary);
        if (fromQuote != null) {
            return fromQuote;
        }
        if (fallbackText != null && !fallbackText.isBlank()
                && !normalize(fallbackText).equals(normalize(primary))) {
            return matchOnPages(pages, fallbackText);
        }
        return null;
    }

    public int locatePage(List<PageText> pages, String sourceQuote, String fallbackText) {
        PageMatch match = locateMatch(pages, sourceQuote, fallbackText);
        return match != null ? match.getPage() : 0;
    }

    public String excerptOnPage(List<PageText> pages, int page, int maxLen) {
        if (pages == null || page < 1) {
            return "";
        }
        return pages.stream()
                .filter(p -> p.getPage() == page)
                .findFirst()
                .map(p -> truncate(p.getText(), maxLen))
                .orElse("");
    }

    private PageMatch matchOnPages(List<PageText> pages, String needle) {
        if (needle == null || needle.isBlank()) {
            return null;
        }
        String normalizedNeedle = normalize(needle);
        if (normalizedNeedle.length() < MIN_SNIPPET_LEN) {
            return null;
        }
        for (int len = Math.min(normalizedNeedle.length(), 120); len >= MIN_SNIPPET_LEN; len -= 8) {
            String snippet = normalizedNeedle.substring(0, len);
            for (PageText page : pages) {
                int[] range = findRangeInPage(page.getText(), snippet);
                if (range != null) {
                    String matched = page.getText().substring(range[0], range[1]);
                    return PageMatch.builder()
                            .page(page.getPage())
                            .matchedSnippet(truncate(matched, 120))
                            .highlightStart(range[0])
                            .highlightEnd(range[1])
                            .build();
                }
            }
        }
        return null;
    }

    /** 在原始页文本中查找归一化片段，返回 [start, end) */
    private int[] findRangeInPage(String pageText, String normalizedSnippet) {
        if (pageText == null || pageText.isEmpty()) {
            return null;
        }
        String normalizedPage = normalize(pageText);
        int idx = normalizedPage.indexOf(normalizedSnippet);
        if (idx < 0) {
            return null;
        }
        int endNorm = idx + normalizedSnippet.length();
        int startOrig = mapNormalizedIndexToOriginal(pageText, idx);
        int endOrig = mapNormalizedIndexToOriginal(pageText, endNorm);
        if (startOrig < 0 || endOrig <= startOrig) {
            return null;
        }
        return new int[]{startOrig, Math.min(endOrig, pageText.length())};
    }

    private int mapNormalizedIndexToOriginal(String original, int normalizedIndex) {
        int norm = 0;
        for (int i = 0; i < original.length(); i++) {
            if (norm >= normalizedIndex) {
                return i;
            }
            if (!Character.isWhitespace(original.charAt(i))) {
                norm++;
            }
        }
        return original.length();
    }

    private String pickNeedle(String sourceQuote, String fallback) {
        if (sourceQuote != null && !sourceQuote.isBlank()) {
            return sourceQuote;
        }
        return fallback;
    }

    private String normalize(String s) {
        return s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.length() <= maxLen) {
            return t;
        }
        return t.substring(0, maxLen) + "…";
    }
}
