package com.ailearning.platform.quiz.llm;

import com.ailearning.platform.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {

    private final RestTemplate restTemplate;
    private final LlmConfigResolver llmConfigResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String chat(String systemPrompt, String userPrompt) {
        LlmConfigResolver.ResolvedLlm llm = llmConfigResolver.resolve();
        log.info("LLM request: provider={}, model={}", llm.providerId(), llm.model());

        String url = llm.apiBase() + "/chat/completions";

        Map<String, Object> body = Map.of(
                "model", llm.model(),
                "temperature", 0.7,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llm.apiKey());

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BusinessException(503, "LLM 服务返回异常");
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new BusinessException(503, "LLM 返回内容为空");
            }
            return content.asText();
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("LLM request failed, provider={}", llm.providerId(), e);
            throw new BusinessException(503, "调用 " + llm.providerId() + " 失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("LLM response parse failed", e);
            throw new BusinessException(503, "解析 LLM 响应失败: " + e.getMessage());
        }
    }
}
