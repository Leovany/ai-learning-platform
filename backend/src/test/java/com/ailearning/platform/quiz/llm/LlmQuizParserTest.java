package com.ailearning.platform.quiz.llm;

import com.ailearning.platform.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LlmQuizParserTest {

    private final LlmQuizParser parser = new LlmQuizParser();

    @Test
    void parse_validJson() {
        String json = """
                {
                  "questions": [
                    {
                      "stem": "测试题干？",
                      "options": { "A": "a", "B": "b", "C": "c", "D": "d" },
                      "correctAnswer": "B",
                      "explanation": "因为 B",
                      "sourcePage": 2,
                      "sourceQuote": "原文摘录"
                    }
                  ]
                }
                """;
        List<LlmGeneratedQuestion> list = parser.parse(json, 5);
        assertEquals(1, list.size());
        assertEquals("测试题干？", list.get(0).getStem());
        assertEquals("B", list.get(0).getCorrectAnswer());
        assertEquals(2, list.get(0).getSourcePage());
    }

    @Test
    void parse_markdownWrappedJson() {
        String raw = """
                ```json
                {"questions":[{"stem":"Q?","options":{"A":"1","B":"2","C":"3","D":"4"},"correctAnswer":"A","explanation":"e"}]}
                ```
                """;
        List<LlmGeneratedQuestion> list = parser.parse(raw, 1);
        assertEquals(1, list.size());
        assertEquals("A", list.get(0).getCorrectAnswer());
    }

    @Test
    void parse_invalidCorrectAnswer_throws() {
        String json = """
                {"questions":[{"stem":"Q","options":{"A":"1","B":"2","C":"3","D":"4"},"correctAnswer":"E","explanation":""}]}
                """;
        assertThrows(BusinessException.class, () -> parser.parse(json, 1));
    }

    @Test
    void parse_truncatesToExpectedCount() {
        String json = """
                {"questions":[
                  {"stem":"Q1","options":{"A":"1","B":"2","C":"3","D":"4"},"correctAnswer":"A","explanation":""},
                  {"stem":"Q2","options":{"A":"1","B":"2","C":"3","D":"4"},"correctAnswer":"B","explanation":""}
                ]}
                """;
        assertEquals(1, parser.parse(json, 1).size());
    }
}
