package com.ailearning.platform.quiz.llm;

import com.ailearning.platform.common.BusinessException;
import com.ailearning.platform.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class LlmConfigResolver {

    private final AppProperties appProperties;

    public ResolvedLlm resolve() {
        String providerId = determineProvider();
        AppProperties.ProviderConfig config = getProviderConfig(providerId);
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw BusinessException.serviceUnavailable(
                    "未配置 " + providerId.toUpperCase(Locale.ROOT) + "_API_KEY，请在 .env 中设置");
        }

        String model = resolveModel(providerId, config);
        String apiBase = normalizeApiBase(providerId, config.getApiBase());

        return new ResolvedLlm(providerId, apiBase, apiKey, model);
    }

    private String determineProvider() {
        String explicit = appProperties.getLlm().getProvider();
        if (explicit != null && !explicit.isBlank() && !"auto".equalsIgnoreCase(explicit)) {
            return normalizeProviderId(explicit);
        }
        String modelHint = appProperties.getLlm().getModel();
        if (modelHint != null && !modelHint.isBlank()) {
            String inferred = inferProviderFromModel(modelHint);
            if (inferred != null) {
                return inferred;
            }
        }
        if (hasApiKey(appProperties.getLlm().getZhipu())) {
            return "zhipu";
        }
        if (hasApiKey(appProperties.getLlm().getDeepseek())) {
            return "deepseek";
        }
        throw BusinessException.serviceUnavailable("未配置任何大模型 API Key（ZHIPU_API_KEY 或 DEEPSEEK_API_KEY）");
    }

    private String inferProviderFromModel(String model) {
        String lower = model.toLowerCase(Locale.ROOT);
        if (lower.startsWith("glm") || lower.startsWith("cogview") || lower.startsWith("charglm")) {
            return "zhipu";
        }
        if (lower.startsWith("deepseek")) {
            return "deepseek";
        }
        return null;
    }

    private String normalizeProviderId(String provider) {
        String id = provider.toLowerCase(Locale.ROOT).trim();
        if ("zhipu".equals(id) || "zhipuai".equals(id) || "bigmodel".equals(id)) {
            return "zhipu";
        }
        if ("deepseek".equals(id)) {
            return "deepseek";
        }
        throw BusinessException.badRequest("不支持的 LLM_PROVIDER: " + provider + "，可选 zhipu | deepseek | auto");
    }

    private AppProperties.ProviderConfig getProviderConfig(String providerId) {
        return "zhipu".equals(providerId)
                ? appProperties.getLlm().getZhipu()
                : appProperties.getLlm().getDeepseek();
    }

    private String resolveModel(String providerId, AppProperties.ProviderConfig config) {
        String globalModel = appProperties.getLlm().getModel();
        if (globalModel != null && !globalModel.isBlank()) {
            String inferred = inferProviderFromModel(globalModel);
            if (inferred == null || inferred.equals(providerId)) {
                return globalModel;
            }
            throw BusinessException.badRequest(
                    "LLM_MODEL=" + globalModel + " 与 LLM_PROVIDER=" + providerId + " 不匹配，请检查配置");
        }
        if (config.getDefaultModel() != null && !config.getDefaultModel().isBlank()) {
            return config.getDefaultModel();
        }
        return "zhipu".equals(providerId) ? "glm-4.7-flash" : "deepseek-chat";
    }

    private String normalizeApiBase(String providerId, String base) {
        if (base == null || base.isBlank()) {
            return "zhipu".equals(providerId)
                    ? "https://open.bigmodel.cn/api/paas/v4"
                    : "https://api.deepseek.com";
        }
        String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        if ("deepseek".equals(providerId) && normalized.endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }

    private boolean hasApiKey(AppProperties.ProviderConfig config) {
        return config.getApiKey() != null && !config.getApiKey().isBlank();
    }

    public record ResolvedLlm(String providerId, String apiBase, String apiKey, String model) {}
}
