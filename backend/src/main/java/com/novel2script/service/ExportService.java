package com.novel2script.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    /**
     * Format and return YAML string.
     */
    public String exportYaml(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            return "";
        }

        try {
            Yaml yaml = new Yaml();
            Object parsed = yaml.load(yamlContent);

            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setAllowUnicode(true);
            options.setIndent(2);
            options.setSplitLines(false);

            Yaml dumper = new Yaml(options);
            return dumper.dump(parsed);
        } catch (Exception e) {
            log.warn("格式化YAML失败，返回原始内容: {}", e.getMessage());
            return yamlContent;
        }
    }

    /**
     * Parse YAML and convert to structured Markdown.
     */
    @SuppressWarnings("unchecked")
    public String exportMarkdown(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            return "";
        }

        Map<String, Object> parsed;
        try {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(yamlContent);
            if (!(loaded instanceof Map)) {
                return "# 无效的剧本格式\n";
            }
            parsed = (Map<String, Object>) loaded;
        } catch (Exception e) {
            log.error("解析YAML失败: {}", e.getMessage());
            return "# YAML 解析失败\n";
        }

        Map<String, Object> scriptNode = getScriptNode(parsed);
        if (scriptNode == null) {
            return "# 无效的剧本结构\n";
        }

        StringBuilder md = new StringBuilder();

        // Title
        Map<String, Object> metadata = getMapField(scriptNode, "metadata");
        if (metadata != null) {
            String title = getStringField(metadata, "title", "未命名剧本");
            md.append("# ").append(title).append("\n\n");
            String author = getStringField(metadata, "author", "");
            if (!author.isBlank()) {
                md.append("**作者**: ").append(author).append("\n\n");
            }
        }

        // Characters
        List<Map<String, Object>> characters = getListField(scriptNode, "characters");
        if (characters != null && !characters.isEmpty()) {
            md.append("## 角色表\n\n");
            for (Map<String, Object> character : characters) {
                String name = getStringField(character, "name", "未知");
                String desc = getStringField(character, "description", "");
                String roleType = getStringField(character, "role_type", "");
                md.append("- **").append(name).append("**");
                if (!roleType.isBlank()) {
                    md.append(" (").append(roleType).append(")");
                }
                if (!desc.isBlank()) {
                    md.append(": ").append(desc);
                }
                md.append("\n");
            }
            md.append("\n");
        }

        // Acts
        List<Map<String, Object>> acts = getListField(scriptNode, "acts");
        if (acts != null) {
            for (Map<String, Object> act : acts) {
                String actTitle = getStringField(act, "title", "");
                int actId = getIntField(act, "act_id", 0);
                md.append("## 第").append(actId).append("幕");
                if (!actTitle.isBlank()) {
                    md.append(": ").append(actTitle);
                }
                md.append("\n\n");

                List<Map<String, Object>> scenes = getListField(act, "scenes");
                if (scenes != null) {
                    for (Map<String, Object> scene : scenes) {
                        int sceneId = getIntField(scene, "scene_id", 0);
                        String setting = getStringField(scene, "setting", "");
                        String mood = getStringField(scene, "mood", "");

                        md.append("### 场 ").append(sceneId);
                        if (!setting.isBlank()) {
                            md.append(" - ").append(setting);
                        }
                        md.append("\n\n");

                        if (!mood.isBlank()) {
                            md.append("*").append(mood).append("*\n\n");
                        }

                        // Scene characters
                        List<?> sceneChars = scene.get("characters") instanceof List
                                ? (List<?>) scene.get("characters") : null;
                        if (sceneChars != null && !sceneChars.isEmpty()) {
                            md.append("**出场角色**: ").append(String.join(", ",
                                    sceneChars.stream().map(String::valueOf).toList())).append("\n\n");
                        }

                        // Beats
                        List<Map<String, Object>> beats = getListField(scene, "beats");
                        if (beats != null) {
                            for (Map<String, Object> beat : beats) {
                                String beatDesc = getStringField(beat, "description", "");
                                if (!beatDesc.isBlank()) {
                                    md.append(beatDesc).append("\n\n");
                                }

                                // Stage directions
                                List<?> stageDirections = beat.get("stage_directions") instanceof List
                                        ? (List<?>) beat.get("stage_directions") : null;
                                if (stageDirections != null) {
                                    for (Object dir : stageDirections) {
                                        md.append("*").append(String.valueOf(dir)).append("*\n\n");
                                    }
                                }

                                // Dialogues
                                List<Map<String, Object>> dialogues = getListField(beat, "dialogues");
                                if (dialogues != null) {
                                    for (Map<String, Object> dialogue : dialogues) {
                                        String speaker = getStringField(dialogue, "speaker", "未知");
                                        String text = getStringField(dialogue, "text", "");
                                        md.append("**").append(speaker).append("**: ").append(text).append("\n\n");
                                    }
                                }

                                // Source mapping as blockquote
                                Map<String, Object> sourceMapping = getMapField(beat, "source_mapping");
                                if (sourceMapping != null) {
                                    String sourceRange = getStringField(sourceMapping, "source_range", "");
                                    String confidence = getStringField(sourceMapping, "confidence", "");
                                    if (!sourceRange.isBlank()) {
                                        md.append("> ").append(sourceRange);
                                        if (!confidence.isBlank()) {
                                            md.append(" [").append(confidence).append("]");
                                        }
                                        md.append("\n\n");
                                    }
                                }
                            }
                        }

                        // Transition
                        String transition = getStringField(scene, "transition", "");
                        if (!transition.isBlank()) {
                            md.append("---\n*").append(transition).append("*\n\n");
                        }
                    }
                }
            }
        }

        return md.toString();
    }

    /**
     * Convert YAML to styled HTML, then generate PDF bytes using OpenHTMLToPDF.
     * Falls back to returning HTML bytes if PDF generation fails.
     */
    public byte[] exportPdf(String yamlContent) {
        String html = yamlToHtml(yamlContent);

        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();

            byte[] pdfBytes = os.toByteArray();
            log.debug("PDF 生成成功，大小: {} bytes", pdfBytes.length);
            return pdfBytes;
        } catch (Exception e) {
            log.warn("PDF 生成失败，回退到 HTML 格式: {}", e.getMessage());
        }

        // Fallback: return HTML bytes
        return html.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Convert screenplay YAML to a complete HTML document with CSS styling.
     */
    @SuppressWarnings("unchecked")
    public String yamlToHtml(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            return buildEmptyHtml("无效的剧本内容");
        }

        Map<String, Object> parsed;
        try {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(yamlContent);
            if (!(loaded instanceof Map)) {
                return buildEmptyHtml("无效的剧本格式");
            }
            parsed = (Map<String, Object>) loaded;
        } catch (Exception e) {
            log.error("解析YAML失败: {}", e.getMessage());
            return buildEmptyHtml("YAML 解析失败: " + e.getMessage());
        }

        Map<String, Object> scriptNode = getScriptNode(parsed);
        if (scriptNode == null) {
            return buildEmptyHtml("无效的剧本结构");
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");

        // Title in <head>
        Map<String, Object> metadata = getMapField(scriptNode, "metadata");
        String title = metadata != null ? getStringField(metadata, "title", "剧本") : "剧本";
        html.append("<title>").append(escapeHtml(title)).append("</title>\n");

        // CSS styling for screenplay-like formatting
        html.append("<style>\n").append(getScreenplayCSS()).append("</style>\n");
        html.append("</head>\n<body>\n");
        html.append("<div class=\"screenplay\">\n");

        // Title header
        html.append("<div class=\"title-page\">\n");
        html.append("<h1>").append(escapeHtml(title)).append("</h1>\n");
        if (metadata != null) {
            String author = getStringField(metadata, "author", "");
            if (!author.isBlank()) {
                html.append("<p class=\"author\">作者: ").append(escapeHtml(author)).append("</p>\n");
            }
        }
        html.append("</div>\n");

        // Characters section
        List<Map<String, Object>> characters = getListField(scriptNode, "characters");
        if (characters != null && !characters.isEmpty()) {
            html.append("<div class=\"characters\">\n");
            html.append("<h2>角色表</h2>\n");
            html.append("<ul>\n");
            for (Map<String, Object> character : characters) {
                String name = getStringField(character, "name", "未知");
                String desc = getStringField(character, "description", "");
                String roleType = getStringField(character, "role_type", "");
                html.append("<li><strong>").append(escapeHtml(name)).append("</strong>");
                if (!roleType.isBlank()) {
                    html.append(" <span class=\"role-type\">(").append(escapeHtml(roleType)).append(")</span>");
                }
                if (!desc.isBlank()) {
                    html.append("<br><span class=\"char-desc\">").append(escapeHtml(desc)).append("</span>");
                }
                html.append("</li>\n");
            }
            html.append("</ul>\n</div>\n");
        }

        // Acts
        List<Map<String, Object>> acts = getListField(scriptNode, "acts");
        if (acts != null) {
            for (Map<String, Object> act : acts) {
                int actId = getIntField(act, "act_id", 0);
                String actTitle = getStringField(act, "title", "");
                html.append("<div class=\"act\">\n");
                html.append("<h2>第").append(actId).append("幕");
                if (!actTitle.isBlank()) {
                    html.append(": ").append(escapeHtml(actTitle));
                }
                html.append("</h2>\n");

                List<Map<String, Object>> scenes = getListField(act, "scenes");
                if (scenes != null) {
                    for (Map<String, Object> scene : scenes) {
                        int sceneId = getIntField(scene, "scene_id", 0);
                        String setting = getStringField(scene, "setting", "");
                        String mood = getStringField(scene, "mood", "");

                        html.append("<div class=\"scene\">\n");
                        html.append("<h3>场 ").append(sceneId);
                        if (!setting.isBlank()) {
                            html.append(" - ").append(escapeHtml(setting));
                        }
                        html.append("</h3>\n");

                        if (!mood.isBlank()) {
                            html.append("<p class=\"mood\">[").append(escapeHtml(mood)).append("]</p>\n");
                        }

                        List<Map<String, Object>> beats = getListField(scene, "beats");
                        if (beats != null) {
                            for (Map<String, Object> beat : beats) {
                                html.append("<div class=\"beat\">\n");

                                String beatDesc = getStringField(beat, "description", "");
                                if (!beatDesc.isBlank()) {
                                    html.append("<p class=\"beat-desc\">").append(escapeHtml(beatDesc)).append("</p>\n");
                                }

                                // Stage directions
                                List<?> stageDirections = beat.get("stage_directions") instanceof List
                                        ? (List<?>) beat.get("stage_directions") : null;
                                if (stageDirections != null) {
                                    for (Object dir : stageDirections) {
                                        html.append("<p class=\"stage-direction\">(")
                                                .append(escapeHtml(String.valueOf(dir)))
                                                .append(")</p>\n");
                                    }
                                }

                                // Dialogues
                                List<Map<String, Object>> dialogues = getListField(beat, "dialogues");
                                if (dialogues != null) {
                                    for (Map<String, Object> dialogue : dialogues) {
                                        String speaker = getStringField(dialogue, "speaker", "未知");
                                        String text = getStringField(dialogue, "text", "");
                                        html.append("<div class=\"dialogue\">\n");
                                        html.append("<p class=\"speaker\">").append(escapeHtml(speaker)).append("</p>\n");
                                        html.append("<p class=\"dialogue-text\">").append(escapeHtml(text)).append("</p>\n");
                                        html.append("</div>\n");
                                    }
                                }

                                // Source mapping
                                Map<String, Object> sourceMapping = getMapField(beat, "source_mapping");
                                if (sourceMapping != null) {
                                    html.append("<div class=\"source-mapping\">\n");
                                    String sourceRange = getStringField(sourceMapping, "source_range", "");
                                    String confidence = getStringField(sourceMapping, "confidence", "");
                                    int srcChapter = getIntField(sourceMapping, "source_chapter", 0);
                                    if (srcChapter > 0) {
                                        html.append("<span class=\"src-chapter\">来源: 第").append(srcChapter).append("章</span>\n");
                                    }
                                    if (!sourceRange.isBlank()) {
                                        html.append("<blockquote>").append(escapeHtml(sourceRange)).append("</blockquote>\n");
                                    }
                                    if (!confidence.isBlank()) {
                                        html.append("<span class=\"confidence\">置信度: ").append(escapeHtml(confidence)).append("</span>\n");
                                    }
                                    html.append("</div>\n");
                                }

                                html.append("</div>\n"); // beat
                            }
                        }

                        // Transition
                        String transition = getStringField(scene, "transition", "");
                        if (!transition.isBlank()) {
                            html.append("<p class=\"transition\">[").append(escapeHtml(transition)).append("]</p>\n");
                        }

                        html.append("</div>\n"); // scene
                    }
                }

                html.append("</div>\n"); // act
            }
        }

        html.append("</div>\n"); // screenplay
        html.append("</body>\n</html>");

        return html.toString();
    }

    // ---- Private helpers ----

    @SuppressWarnings("unchecked")
    private Map<String, Object> getScriptNode(Map<String, Object> parsed) {
        if (parsed.containsKey("script") && parsed.get("script") instanceof Map) {
            return (Map<String, Object>) parsed.get("script");
        }
        // Fallback: treat the root as the script node
        if (parsed.containsKey("metadata") || parsed.containsKey("acts")) {
            return parsed;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMapField(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return (value instanceof Map) ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getListField(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<Map<String, Object>>) value;
        }
        return null;
    }

    private String getStringField(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private int getIntField(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String buildEmptyHtml(String message) {
        return "<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n" +
                "<meta charset=\"UTF-8\">\n<title>剧本导出</title>\n</head>\n<body>\n" +
                "<h1>" + escapeHtml(message) + "</h1>\n</body>\n</html>";
    }

    private String getScreenplayCSS() {
        return """
                body {
                    font-family: "SimSun", "Songti SC", "Noto Serif CJK SC", serif;
                    background: #f5f5f0;
                    margin: 0;
                    padding: 20px;
                    color: #333;
                }
                .screenplay {
                    max-width: 800px;
                    margin: 0 auto;
                    background: #fff;
                    padding: 40px 60px;
                    box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                }
                .title-page {
                    text-align: center;
                    padding: 60px 0;
                    border-bottom: 2px solid #333;
                    margin-bottom: 40px;
                }
                .title-page h1 {
                    font-size: 2.5em;
                    margin: 0 0 20px 0;
                    letter-spacing: 0.1em;
                }
                .title-page .author {
                    font-size: 1.2em;
                    color: #666;
                }
                .characters {
                    margin-bottom: 40px;
                    padding: 20px;
                    background: #fafafa;
                    border-left: 4px solid #666;
                }
                .characters h2 {
                    margin-top: 0;
                    font-size: 1.4em;
                }
                .characters ul {
                    list-style: none;
                    padding: 0;
                }
                .characters li {
                    margin: 10px 0;
                    line-height: 1.6;
                }
                .role-type {
                    color: #888;
                    font-size: 0.9em;
                }
                .char-desc {
                    color: #666;
                    font-size: 0.9em;
                }
                .act {
                    margin: 40px 0;
                    page-break-before: always;
                }
                .act h2 {
                    font-size: 1.8em;
                    text-align: center;
                    border-bottom: 1px solid #ccc;
                    padding-bottom: 10px;
                    margin-bottom: 30px;
                }
                .scene {
                    margin: 30px 0;
                }
                .scene h3 {
                    font-size: 1.2em;
                    text-transform: uppercase;
                    border-bottom: 1px dotted #999;
                    padding-bottom: 5px;
                    margin-bottom: 15px;
                }
                .mood {
                    font-style: italic;
                    color: #888;
                    text-align: center;
                }
                .beat {
                    margin: 20px 0;
                }
                .beat-desc {
                    font-style: italic;
                    color: #555;
                    margin: 10px 0;
                }
                .stage-direction {
                    font-style: italic;
                    color: #777;
                    text-align: center;
                    margin: 10px 20px;
                }
                .dialogue {
                    margin: 15px 0 15px 120px;
                }
                .speaker {
                    text-align: center;
                    font-weight: bold;
                    text-transform: uppercase;
                    margin: 0 0 5px -60px;
                    letter-spacing: 0.05em;
                }
                .dialogue-text {
                    margin: 0;
                    text-align: center;
                    line-height: 1.6;
                }
                .source-mapping {
                    margin: 10px 0;
                    padding: 8px 12px;
                    background: #f9f9f9;
                    border-left: 3px solid #ddd;
                    font-size: 0.85em;
                    color: #888;
                }
                .source-mapping blockquote {
                    margin: 5px 0;
                    font-style: italic;
                }
                .confidence {
                    font-size: 0.85em;
                    color: #aaa;
                }
                .transition {
                    text-align: center;
                    font-style: italic;
                    color: #999;
                    margin: 20px 0;
                    font-size: 0.9em;
                }
                @media print {
                    body {
                        background: #fff;
                        padding: 0;
                    }
                    .screenplay {
                        box-shadow: none;
                        padding: 20px 40px;
                    }
                }
                """;
    }
}
