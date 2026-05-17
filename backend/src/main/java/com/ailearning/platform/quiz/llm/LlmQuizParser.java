package com.ailearning.platform.quiz.llm;

import com.ailearning.platform.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class LlmQuizParser {

    private static final Set<String> OPTION_KEYS = Set.of("A", "B", "C", "D");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<LlmGeneratedQuestion> parse(String rawContent, int expectedCount) {
        String json = extractJson(rawContent);
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode questionsNode = root.has("questions") ? root.get("questions") : root;
            if (!questionsNode.isArray()) {
                throw BusinessException.badRequest("LLM 返回格式无效：缺少 questions 数组");
            }
            List<LlmGeneratedQuestion> result = new ArrayList<>();
            for (JsonNode node : questionsNode) {
                result.add(parseQuestion(node));
            }
            if (result.isEmpty()) {
                throw BusinessException.badRequest("LLM 未生成任何题目");
            }
            return result.subList(0, Math.min(result.size(), expectedCount));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.badRequest("解析 LLM 响应失败: " + e.getMessage());
        }
    }

    private LlmGeneratedQuestion parseQuestion(JsonNode node) {
        String stem = text(node, "stem");
        if (stem.isBlank()) {
            throw BusinessException.badRequest("题目题干不能为空");
        }
        JsonNode optionsNode = node.get("options");
        if (optionsNode == null || !optionsNode.isObject()) {
            throw BusinessException.badRequest("题目选项格式无效");
        }
        Map<String, String> options = objectMapper.convertValue(
                optionsNode,
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class)
        );
        for (String key : OPTION_KEYS) {
            if (!options.containsKey(key) || options.get(key) == null || options.get(key).isBlank()) {
                throw BusinessException.badRequest("题目缺少选项 " + key);
            }
        }
        String correct = text(node, "correctAnswer").toUpperCase(Locale.ROOT);
        if (!OPTION_KEYS.contains(correct)) {
            throw BusinessException.badRequest("正确答案必须是 A/B/C/D");
        }
        LlmGeneratedQuestion q = new LlmGeneratedQuestion();
        q.setStem(stem);
        q.setOptions(options);
        q.setCorrectAnswer(correct);
        q.setExplanation(text(node, "explanation"));
        q.setSourceQuote(text(node, "sourceQuote"));
        JsonNode pageNode = node.get("sourcePage");
        if (pageNode != null && pageNode.isInt() && pageNode.asInt() > 0) {
            q.setSourcePage(pageNode.asInt());
        }
        return q;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText().trim();
    }

    private String extractJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }
        int objStart = trimmed.indexOf('{');
        int arrStart = trimmed.indexOf('[');
        if (objStart >= 0 && (arrStart < 0 || objStart < arrStart)) {
            return trimmed.substring(objStart, trimmed.lastIndexOf('}') + 1);
        }
        if (arrStart >= 0) {
            return "{\"questions\":" + trimmed.substring(arrStart, trimmed.lastIndexOf(']') + 1) + "}";
        }
        return trimmed;
    }
}
