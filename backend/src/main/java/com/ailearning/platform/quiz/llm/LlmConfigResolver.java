package com.ailearning.platform.quiz.llm;

import com.ailearning.platform.common.BusinessException;
import com.ailearning.platform.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LlmConfigResolver {

    private final AppProperties appProperties;

    /**
     * 获取所有可用的 LLM 配置列表（按优先级排序）
     */
    public List<ResolvedLlm> resolveAll() {
        List<String> providerIds = getProviderPriorityList();
        List<ResolvedLlm> resolved = new ArrayList<>();
        
        for (String providerId : providerIds) {
            ResolvedLlm llm = resolveSingle(providerId);
            resolved.add(llm);
        }
        
        if (resolved.isEmpty()) {
            throw BusinessException.serviceUnavailable("未配置任何有效的大模型 API Key");
        }
        
        return resolved;
    }

    /**
     * 获取单个 LLM 配置（返回第一个可用的）
     */
    public ResolvedLlm resolve() {
        return resolveAll().get(0);
    }

    /**
     * 获取提供商优先级列表
     */
    private List<String> getProviderPriorityList() {
        List<String> configured = appProperties.getLlm().getProviders();
        
        if (configured != null && !configured.isEmpty()) {
            return configured.stream()
                    .map(this::normalizeProviderId)
                    .filter(this::hasValidApiKey)
                    .collect(Collectors.toList());
        }
        
        List<String> defaultOrder = List.of("zhipu", "deepseek", "qwen");
        return defaultOrder.stream()
                .filter(this::hasValidApiKey)
                .collect(Collectors.toList());
    }

    private boolean hasValidApiKey(String providerId) {
        AppProperties.ProviderConfig config = getProviderConfig(providerId);
        return config != null && config.getApiKey() != null && !config.getApiKey().isBlank();
    }

    private ResolvedLlm resolveSingle(String providerId) {
        AppProperties.ProviderConfig config = getProviderConfig(providerId);
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw BusinessException.serviceUnavailable(
                    "未配置 " + providerId.toUpperCase(Locale.ROOT) + "_API_KEY");
        }

        String model = resolveModel(providerId, config);
        String apiBase = normalizeApiBase(providerId, config.getApiBase());

        return new ResolvedLlm(providerId, apiBase, apiKey, model);
    }

    private AppProperties.ProviderConfig getProviderConfig(String providerId) {
        return switch (providerId) {
            case "zhipu" -> appProperties.getLlm().getZhipu();
            case "deepseek" -> appProperties.getLlm().getDeepseek();
            case "qwen" -> appProperties.getLlm().getQwen();
            default -> throw BusinessException.badRequest("不支持的 LLM 提供商: " + providerId);
        };
    }

    private String resolveModel(String providerId, AppProperties.ProviderConfig config) {
        String globalModel = appProperties.getLlm().getModel();
        if (globalModel != null && !globalModel.isBlank()) {
            return globalModel;
        }
        if (config.getDefaultModel() != null && !config.getDefaultModel().isBlank()) {
            return config.getDefaultModel();
        }
        return getDefaultModel(providerId);
    }

    private String getDefaultModel(String providerId) {
        return switch (providerId) {
            case "zhipu" -> "glm-4.7-flash";
            case "deepseek" -> "deepseek-chat";
            case "qwen" -> "qwen-plus";
            default -> "glm-4.7-flash";
        };
    }

    private String normalizeApiBase(String providerId, String base) {
        if (base == null || base.isBlank()) {
            return switch (providerId) {
                case "zhipu" -> "https://open.bigmodel.cn/api/paas/v4";
                case "deepseek" -> "https://api.deepseek.com";
                case "qwen" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
                default -> throw BusinessException.badRequest("不支持的 LLM 提供商: " + providerId);
            };
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String normalizeProviderId(String provider) {
        String id = provider.toLowerCase(Locale.ROOT).trim();
        return switch (id) {
            case "zhipu", "zhipuai", "bigmodel" -> "zhipu";
            case "deepseek" -> "deepseek";
            case "qwen", "aliyun", "bailian", "tongyi", "dashscope" -> "qwen";
            default -> throw BusinessException.badRequest(
                    "不支持的 LLM_PROVIDER: " + provider + "，可选 zhipu | deepseek | qwen");
        };
    }

    public record ResolvedLlm(String providerId, String apiBase, String apiKey, String model) {}
}
