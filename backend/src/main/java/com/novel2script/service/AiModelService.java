package com.novel2script.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.config.DeepSeekConfig;
import com.novel2script.dto.AiModelConfigRequest;
import com.novel2script.dto.AiModelConfigResponse;
import com.novel2script.entity.AiModelConfig;
import com.novel2script.exception.GlobalExceptionHandler.BadRequestException;
import com.novel2script.exception.GlobalExceptionHandler.ResourceNotFoundException;
import com.novel2script.repository.AiModelConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelService {

    private final AiModelConfigRepository repository;
    private final DeepSeekConfig deepSeekConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, String> DEFAULT_BASE_URLS = Map.of(
            "deepseek", "https://api.deepseek.com",
            "qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "openai", "https://api.openai.com/v1",
            "zhipu", "https://open.bigmodel.cn/api/paas/v4",
            "moonshot", "https://api.moonshot.cn/v1"
    );

    public List<AiModelConfigResponse> getAllModels() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public AiModelConfigResponse createModel(AiModelConfigRequest request) {
        String baseUrl = request.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = getDefaultBaseUrl(request.getProvider());
            if (baseUrl == null) {
                throw new BadRequestException("未知厂商，请手动提供 baseUrl");
            }
        }

        AiModelConfig config = AiModelConfig.builder()
                .provider(request.getProvider())
                .modelName(request.getModelName())
                .apiKey(request.getApiKey())
                .baseUrl(baseUrl)
                .enabled(false)
                .active(false)
                .build();

        AiModelConfig saved = repository.save(config);
        log.info("创建 AI 模型配置: id={}, provider={}, model={}", saved.getId(), saved.getProvider(), saved.getModelName());
        return toResponse(saved);
    }

    public AiModelConfigResponse updateModel(Long id, AiModelConfigRequest request) {
        AiModelConfig config = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AI 模型配置不存在: " + id));

        config.setProvider(request.getProvider());
        config.setModelName(request.getModelName());
        config.setApiKey(request.getApiKey());

        String baseUrl = request.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = getDefaultBaseUrl(request.getProvider());
            if (baseUrl == null) {
                throw new BadRequestException("未知厂商，请手动提供 baseUrl");
            }
        }
        config.setBaseUrl(baseUrl);

        AiModelConfig saved = repository.save(config);
        log.info("更新 AI 模型配置: id={}", id);
        return toResponse(saved);
    }

    public void deleteModel(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("AI 模型配置不存在: " + id);
        }
        repository.deleteById(id);
        log.info("删除 AI 模型配置: id={}", id);
    }

    public AiModelConfigResponse toggleEnabled(Long id) {
        AiModelConfig config = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AI 模型配置不存在: " + id));

        boolean newEnabled = !Boolean.TRUE.equals(config.getEnabled());
        config.setEnabled(newEnabled);

        // If disabling, also deactivate
        if (!newEnabled) {
            config.setActive(false);
        }

        AiModelConfig saved = repository.save(config);
        log.info("切换 AI 模型启用状态: id={}, enabled={}", id, newEnabled);
        return toResponse(saved);
    }

    public AiModelConfigResponse setActive(Long id) {
        AiModelConfig config = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AI 模型配置不存在: " + id));

        // Deactivate all other models
        List<AiModelConfig> allModels = repository.findByEnabledTrue();
        for (AiModelConfig model : allModels) {
            if (Boolean.TRUE.equals(model.getActive())) {
                model.setActive(false);
                repository.save(model);
            }
        }

        // Also deactivate any model that might be active but not enabled
        repository.findByActiveTrue().ifPresent(activeModel -> {
            if (!activeModel.getId().equals(id)) {
                activeModel.setActive(false);
                repository.save(activeModel);
            }
        });

        // Set this model as active and enabled
        config.setActive(true);
        config.setEnabled(true);
        AiModelConfig saved = repository.save(config);

        log.info("设置活跃 AI 模型: id={}, provider={}, model={}", id, saved.getProvider(), saved.getModelName());
        return toResponse(saved);
    }

    @SuppressWarnings("unchecked")
    public AiModelConfigResponse testModel(Long id) {
        AiModelConfig config = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AI 模型配置不存在: " + id));

        boolean success = testModelConnection(config);

        config.setLastTestStatus(success ? "success" : "fail");
        config.setLastTestAt(LocalDateTime.now());
        AiModelConfig saved = repository.save(config);

        log.info("测试 AI 模型: id={}, result={}", id, success ? "success" : "fail");
        return toResponse(saved);
    }

    /**
     * Get the active model config. Falls back to default DeepSeek config if none is active.
     */
    public AiModelConfig getActiveModel() {
        Optional<AiModelConfig> activeModel = repository.findByActiveTrue();
        if (activeModel.isPresent()) {
            return activeModel.get();
        }

        // Fallback to default DeepSeek configuration from application properties
        log.info("未找到活跃模型，使用默认 DeepSeek 配置");
        return AiModelConfig.builder()
                .provider("deepseek")
                .modelName(deepSeekConfig.getModel() != null ? deepSeekConfig.getModel() : "deepseek-chat")
                .apiKey(deepSeekConfig.getApiKey())
                .baseUrl(deepSeekConfig.getBaseUrl() != null ? deepSeekConfig.getBaseUrl() : "https://api.deepseek.com")
                .enabled(true)
                .active(true)
                .build();
    }

    // ---- Private helpers ----

    private String maskApiKey(String key) {
        if (key == null || key.length() <= 6) {
            return "****";
        }
        return key.substring(0, 6) + "****";
    }

    private String getDefaultBaseUrl(String provider) {
        if (provider == null) return null;
        return DEFAULT_BASE_URLS.get(provider.toLowerCase());
    }

    private AiModelConfigResponse toResponse(AiModelConfig config) {
        return AiModelConfigResponse.builder()
                .id(config.getId())
                .provider(config.getProvider())
                .modelName(config.getModelName())
                .baseUrl(config.getBaseUrl())
                .enabled(config.getEnabled())
                .active(config.getActive())
                .lastTestStatus(config.getLastTestStatus())
                .lastTestAt(config.getLastTestAt())
                .createdAt(config.getCreatedAt())
                .apiKeyMasked(maskApiKey(config.getApiKey()))
                .build();
    }

    @SuppressWarnings("unchecked")
    private boolean testModelConnection(AiModelConfig config) {
        try {
            RestClient client = RestClient.builder()
                    .baseUrl(config.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + config.getApiKey())
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", config.getModelName());
            requestBody.put("max_tokens", 10);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "user", "content", "Hello"));
            requestBody.put("messages", messages);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            Map<String, Object> response = client.post()
                    .uri("/chat/completions")
                    .body(jsonBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return false;
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            return choices != null && !choices.isEmpty();

        } catch (RestClientException e) {
            log.warn("模型连接测试失败: provider={}, model={}, error={}", config.getProvider(), config.getModelName(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("模型连接测试异常: {}", e.getMessage());
            return false;
        }
    }
}
