package com.novel2script.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.config.DeepSeekConfig;
import com.novel2script.exception.GlobalExceptionHandler.DeepSeekApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekService {

    private final RestClient deepSeekRestClient;
    private final DeepSeekConfig deepSeekConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_RETRIES = 2;

    /**
     * Generate a summary for a chapter in Chinese.
     */
    public String generateChapterSummary(String chapterText, int chapterNumber) {
        log.debug("生成第{}章摘要，文本长度: {}", chapterNumber, chapterText.length());

        String systemPrompt = "你是一位专业的文学编辑，擅长总结小说章节内容。请用简洁的中文总结以下章节的核心情节、人物活动和重要事件。总结应控制在200-400字之间。";
        String userPrompt = "请总结第" + chapterNumber + "章的内容：\n\n" + truncateText(chapterText, 6000);

        return callDeepSeek(systemPrompt, userPrompt);
    }

    /**
     * Extract character information from the full text of all chapters.
     * Returns a list of maps with fields: name, description, role_type.
     * Includes retry logic (max 2 retries).
     */
    public List<Map<String, Object>> extractCharacters(String allChaptersText) {
        log.debug("提取角色信息，文本长度: {}", allChaptersText.length());

        String systemPrompt = "你是一位专业的文学分析专家。请仔细阅读小说文本，提取所有重要角色信息。" +
                "请以JSON数组格式返回结果，每个角色包含以下字段：\n" +
                "- name: 角色姓名\n" +
                "- description: 角色描述（性格、外貌、特征等）\n" +
                "- role_type: 角色类型，只能是 protagonist（主角）、antagonist（反派）、supporting（配角）、minor（次要角色）之一\n\n" +
                "只返回JSON数组，不要包含任何其他文字或markdown代码标记。";

        String userPrompt = "请分析以下小说文本中的所有角色：\n\n" + truncateText(allChaptersText, 8000);

        String response = callDeepSeekWithRetry(systemPrompt, userPrompt, MAX_RETRIES);
        return parseJsonArray(response, "角色");
    }

    /**
     * Extract location information from the full text.
     * Returns a list of maps with fields: name, description.
     */
    public List<Map<String, Object>> extractLocations(String allChaptersText) {
        log.debug("提取场景/地点信息，文本长度: {}", allChaptersText.length());

        String systemPrompt = "你是一位专业的文学分析专家。请仔细阅读小说文本，提取所有重要的场景和地点信息。" +
                "请以JSON数组格式返回结果，每个地点包含以下字段：\n" +
                "- name: 地点名称\n" +
                "- description: 地点描述（环境特征、氛围等）\n\n" +
                "只返回JSON数组，不要包含任何其他文字或markdown代码标记。";

        String userPrompt = "请分析以下小说文本中的所有场景和地点：\n\n" + truncateText(allChaptersText, 8000);

        String response = callDeepSeek(systemPrompt, userPrompt);
        return parseJsonArray(response, "地点");
    }

    /**
     * Extract events from a chapter.
     * Returns a list of maps with fields: chapter, description, characters_involved.
     */
    public List<Map<String, Object>> extractEvents(String chapterText, int chapterNumber) {
        log.debug("提取第{}章事件，文本长度: {}", chapterNumber, chapterText.length());

        String systemPrompt = "你是一位专业的文学分析专家。请仔细阅读章节文本，提取其中的关键事件。" +
                "请以JSON数组格式返回结果，每个事件包含以下字段：\n" +
                "- chapter: 章节编号（整数）\n" +
                "- description: 事件描述\n" +
                "- characters_involved: 涉及的角色名称列表（字符串数组）\n\n" +
                "只返回JSON数组，不要包含任何其他文字或markdown代码标记。";

        String userPrompt = "请分析第" + chapterNumber + "章中的关键事件：\n\n" + truncateText(chapterText, 6000);

        String response = callDeepSeek(systemPrompt, userPrompt);
        return parseJsonArray(response, "事件");
    }

    /**
     * THE CORE METHOD: Generate YAML screenplay for a chapter.
     * Produces structured YAML with acts, scenes, beats, dialogues, and source mappings.
     */
    public String generateScreenplay(String chapterText, int chapterNumber,
                                      String charactersJson, String locationsJson,
                                      String previousContext) {
        log.debug("生成第{}章剧本，文本长度: {}", chapterNumber, chapterText.length());

        String systemPrompt = buildScreenplaySystemPrompt(charactersJson, locationsJson);
        String userPrompt = buildScreenplayUserPrompt(chapterText, chapterNumber, previousContext);

        String response = callDeepSeek(systemPrompt, userPrompt);

        // Strip markdown code fences if present
        response = stripCodeFences(response);

        return response;
    }

    // ---- Private helper methods ----

    private String buildScreenplaySystemPrompt(String charactersJson, String locationsJson) {
        return "你是一位世界级的编剧，擅长将小说改编为专业的影视剧本。你的任务是将小说章节转化为结构化的YAML剧本格式。\n\n" +
                "【严格要求】\n" +
                "1. 只输出合法的YAML内容，不要包含任何markdown代码标记（如```yaml）、解释文字或注释\n" +
                "2. 必须严格遵循以下YAML结构\n" +
                "3. 对话要自然流畅，捕捉原文的精髓和情感\n" +
                "4. 使用提供的角色列表和地点列表保持一致性\n\n" +
                "【YAML结构要求】\n" +
                "acts:\n" +
                "  - act_id: (整数，幕的编号)\n" +
                "    title: (幕的标题)\n" +
                "    scenes:\n" +
                "      - scene_id: (整数，场的编号)\n" +
                "        setting: (场景描述，包含时间、地点、氛围)\n" +
                "        characters: (本场出场角色列表)\n" +
                "        mood: (情绪基调)\n" +
                "        transition: (转场方式)\n" +
                "        beats:\n" +
                "          - beat_id: (整数，节拍编号)\n" +
                "            description: (节拍描述，动作和情节推进)\n" +
                "            dialogues:\n" +
                "              - speaker: (说话人姓名)\n" +
                "                text: (台词内容)\n" +
                "            stage_directions: (舞台指示列表，描述动作、表情、走位等)\n" +
                "            source_mapping:\n" +
                "              source_chapter: (来源章节编号)\n" +
                "              source_range: (原文引用，对应的原文片段)\n" +
                "              confidence: (置信度: high/medium/low)\n\n" +
                "【已知角色信息】\n" + charactersJson + "\n\n" +
                "【已知地点信息】\n" + locationsJson;
    }

    private String buildScreenplayUserPrompt(String chapterText, int chapterNumber, String previousContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请将以下第").append(chapterNumber).append("章小说内容改编为YAML格式的剧本。\n\n");

        if (previousContext != null && !previousContext.isBlank()) {
            prompt.append("【前文剧情概要（保持连贯性）】\n").append(truncateText(previousContext, 1000)).append("\n\n");
        }

        prompt.append("【第").append(chapterNumber).append("章原文】\n").append(truncateText(chapterText, 6000));

        return prompt.toString();
    }

    /**
     * Call the DeepSeek chat completion API.
     */
    @SuppressWarnings("unchecked")
    private String callDeepSeek(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", deepSeekConfig.getModel() != null ? deepSeekConfig.getModel() : "deepseek-chat");
            requestBody.put("temperature", deepSeekConfig.getTemperature() > 0 ? deepSeekConfig.getTemperature() : 0.7);
            requestBody.put("max_tokens", deepSeekConfig.getMaxTokens() > 0 ? deepSeekConfig.getMaxTokens() : 8192);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userPrompt));
            requestBody.put("messages", messages);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.debug("DeepSeek API 请求: model={}, messages数={}", requestBody.get("model"), messages.size());

            Map<String, Object> response = deepSeekRestClient.post()
                    .uri("/chat/completions")
                    .body(jsonBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new DeepSeekApiException("DeepSeek API 返回空响应");
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new DeepSeekApiException("DeepSeek API 未返回有效结果");
            }

            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) {
                throw new DeepSeekApiException("DeepSeek API 响应中缺少 message 字段");
            }

            String content = (String) message.get("content");
            log.debug("DeepSeek API 响应长度: {}", content != null ? content.length() : 0);

            return content;

        } catch (DeepSeekApiException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("DeepSeek API 调用失败: {}", e.getMessage());
            throw new DeepSeekApiException("DeepSeek API 调用失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("处理 DeepSeek API 响应时出错: {}", e.getMessage());
            throw new DeepSeekApiException("处理 API 响应失败: " + e.getMessage(), e);
        }
    }

    /**
     * Call DeepSeek with retry logic.
     */
    private String callDeepSeekWithRetry(String systemPrompt, String userPrompt, int maxRetries) {
        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                String response = callDeepSeek(systemPrompt, userPrompt);
                // Validate that response can be parsed as JSON array
                String cleaned = stripCodeFences(response);
                objectMapper.readTree(cleaned);
                return response;
            } catch (Exception e) {
                lastException = e;
                log.warn("DeepSeek API 调用第 {} 次尝试失败: {}", attempt + 1, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000L * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw new DeepSeekApiException("DeepSeek API 调用在 " + (maxRetries + 1) + " 次尝试后仍然失败: "
                + (lastException != null ? lastException.getMessage() : "未知错误"));
    }

    /**
     * Parse a JSON array string from the model response.
     */
    private List<Map<String, Object>> parseJsonArray(String response, String entityName) {
        try {
            String cleaned = stripCodeFences(response);
            return objectMapper.readValue(cleaned, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("解析{}JSON失败，原始响应: {}", entityName, truncateText(response, 500));
            log.error("解析异常: {}", e.getMessage());
            throw new DeepSeekApiException("解析" + entityName + "数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * Strip markdown code fences from the response.
     */
    private String stripCodeFences(String text) {
        if (text == null) return "";
        String stripped = text.trim();
        // Remove ```yaml ... ``` or ``` ... ```
        if (stripped.startsWith("```yaml")) {
            stripped = stripped.substring(7);
        } else if (stripped.startsWith("```json")) {
            stripped = stripped.substring(7);
        } else if (stripped.startsWith("```")) {
            stripped = stripped.substring(3);
        }
        if (stripped.endsWith("```")) {
            stripped = stripped.substring(0, stripped.length() - 3);
        }
        return stripped.trim();
    }

    /**
     * Truncate text to a maximum length, appending ellipsis if truncated.
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n...(文本已截断)";
    }
}
