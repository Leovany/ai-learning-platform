package com.ailearning.platform.quiz.llm;

import com.ailearning.platform.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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
    
    private static final int MAX_RETRIES_PER_PROVIDER = 2;
    private static final long INITIAL_DELAY_MS = 2000;

    /**
     * 调用 LLM，支持多提供商故障转移
     */
    public String chat(String systemPrompt, String userPrompt) {
        List<LlmConfigResolver.ResolvedLlm> llmList = llmConfigResolver.resolveAll();
        Exception lastException = null;
        
        for (int i = 0; i < llmList.size(); i++) {
            LlmConfigResolver.ResolvedLlm llm = llmList.get(i);
            log.info("尝试 LLM [{}/{}]: provider={}, model={}", i + 1, llmList.size(), llm.providerId(), llm.model());
            
            try {
                String result = chatWithRetry(llm, systemPrompt, userPrompt);
                log.info("LLM 调用成功: provider={}", llm.providerId());
                return result;
            } catch (Exception e) {
                lastException = e;
                log.warn("LLM 调用失败 [{}/{}]: provider={}, error={}", 
                        i + 1, llmList.size(), llm.providerId(), e.getMessage());
                
                // 如果不是最后一个提供商，继续尝试下一个
                if (i < llmList.size() - 1) {
                    log.info("切换到下一个 LLM 提供商...");
                }
            }
        }
        
        // 所有提供商都失败了
        String errorMsg = "所有配置的大模型调用都失败了";
        if (lastException != null) {
            errorMsg += ": " + lastException.getMessage();
        }
        throw new BusinessException(503, errorMsg);
    }

    /**
     * 调用单个 LLM，带重试机制
     */
    private String chatWithRetry(LlmConfigResolver.ResolvedLlm llm, String systemPrompt, String userPrompt) {
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

        int attempts = 0;
        long delay = INITIAL_DELAY_MS;
        
        while (attempts < MAX_RETRIES_PER_PROVIDER) {
            attempts++;
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
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS && attempts < MAX_RETRIES_PER_PROVIDER) {
                    log.warn("LLM request rate limited (429), attempt {}/{}, retrying in {}ms", 
                            attempts, MAX_RETRIES_PER_PROVIDER, delay);
                    try {
                        Thread.sleep(delay);
                        delay *= 2;
                        continue;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException(503, "请求被中断");
                    }
                }
                throw e;
            } catch (BusinessException e) {
                throw e;
            } catch (RestClientException e) {
                throw new BusinessException(503, "调用 " + llm.providerId() + " 失败: " + e.getMessage());
            } catch (Exception e) {
                throw new BusinessException(503, "解析 " + llm.providerId() + " 响应失败: " + e.getMessage());
            }
        }
        
        throw new BusinessException(503, "调用 " + llm.providerId() + " 失败: 服务限流，请稍后再试");
    }
}
