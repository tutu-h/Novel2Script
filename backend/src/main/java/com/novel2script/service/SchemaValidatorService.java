package com.novel2script.service;

import com.novel2script.dto.ValidationResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
public class SchemaValidatorService {

    private Map<String, Object> schema;

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("schema/script_schema.yaml");
            Yaml yaml = new Yaml();
            try (InputStream is = resource.getInputStream()) {
                schema = yaml.load(is);
            }
            log.debug("剧本YAML schema 已加载");
        } catch (Exception e) {
            log.warn("无法加载 schema 文件，将使用内置验证规则: {}", e.getMessage());
            schema = Collections.emptyMap();
        }
    }

    /**
     * Validate YAML content against the screenplay schema.
     * Returns a ValidationResultResponse with is_valid, errors, and warnings.
     */
    @SuppressWarnings("unchecked")
    public ValidationResultResponse validate(String yamlContent) {
        List<Map<String, String>> errors = new ArrayList<>();
        List<Map<String, String>> warnings = new ArrayList<>();

        if (yamlContent == null || yamlContent.isBlank()) {
            errors.add(errorEntry("root", "YAML 内容为空"));
            return ValidationResultResponse.builder()
                    .valid(false)
                    .errors(errors)
                    .warnings(warnings)
                    .build();
        }

        // Parse YAML
        Map<String, Object> parsed;
        try {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(yamlContent);
            if (!(loaded instanceof Map)) {
                errors.add(errorEntry("root", "YAML 根节点必须是对象类型"));
                return ValidationResultResponse.builder()
                        .valid(false)
                        .errors(errors)
                        .warnings(warnings)
                        .build();
            }
            parsed = (Map<String, Object>) loaded;
        } catch (Exception e) {
            errors.add(errorEntry("root", "YAML 解析失败: " + e.getMessage()));
            return ValidationResultResponse.builder()
                    .valid(false)
                    .errors(errors)
                    .warnings(warnings)
                    .build();
        }

        // Validate root: must have "script"
        if (!parsed.containsKey("script")) {
            errors.add(errorEntry("script", "缺少必需的顶层字段: script"));
        } else {
            Object scriptObj = parsed.get("script");
            if (!(scriptObj instanceof Map)) {
                errors.add(errorEntry("script", "script 字段必须是对象类型"));
            } else {
                Map<String, Object> scriptNode = (Map<String, Object>) scriptObj;
                validateScriptNode(scriptNode, errors, warnings);
            }
        }

        boolean isValid = errors.isEmpty();
        log.debug("YAML 验证完成: valid={}, errors={}, warnings={}", isValid, errors.size(), warnings.size());

        return ValidationResultResponse.builder()
                .valid(isValid)
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    /**
     * Auto-fix common errors in YAML content.
     * Ensures required fields exist with defaults, fixes type mismatches.
     */
    @SuppressWarnings("unchecked")
    public String autoFixCommonErrors(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            return buildMinimalValidYaml();
        }

        Map<String, Object> parsed;
        try {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(yamlContent);
            if (!(loaded instanceof Map)) {
                return buildMinimalValidYaml();
            }
            parsed = new LinkedHashMap<>((Map<String, Object>) loaded);
        } catch (Exception e) {
            log.warn("解析YAML失败，返回最小有效结构: {}", e.getMessage());
            return buildMinimalValidYaml();
        }

        // Ensure "script" key exists
        if (!parsed.containsKey("script") || !(parsed.get("script") instanceof Map)) {
            parsed.put("script", new LinkedHashMap<>());
        }

        Map<String, Object> scriptNode = (Map<String, Object>) parsed.get("script");

        // Ensure metadata exists with title
        if (!scriptNode.containsKey("metadata") || !(scriptNode.get("metadata") instanceof Map)) {
            scriptNode.put("metadata", new LinkedHashMap<>());
        }
        Map<String, Object> metadata = (Map<String, Object>) scriptNode.get("metadata");
        if (!metadata.containsKey("title") || metadata.get("title") == null) {
            metadata.put("title", "未命名剧本");
        }

        // Ensure acts exists and is a non-empty list
        if (!scriptNode.containsKey("acts") || !(scriptNode.get("acts") instanceof List)) {
            scriptNode.put("acts", new ArrayList<>());
        }
        List<Map<String, Object>> acts = (List<Map<String, Object>>) scriptNode.get("acts");
        if (acts.isEmpty()) {
            Map<String, Object> defaultAct = new LinkedHashMap<>();
            defaultAct.put("act_id", 1);
            defaultAct.put("title", "第一幕");
            Map<String, Object> defaultScene = new LinkedHashMap<>();
            defaultScene.put("scene_id", 1);
            defaultScene.put("setting", "默认场景");
            defaultScene.put("beats", new ArrayList<>());
            defaultAct.put("scenes", List.of(defaultScene));
            acts.add(defaultAct);
        }

        // Fix each act
        for (int i = 0; i < acts.size(); i++) {
            Map<String, Object> act = acts.get(i);
            if (!(act instanceof Map)) {
                Map<String, Object> fixedAct = new LinkedHashMap<>();
                fixedAct.put("act_id", i + 1);
                fixedAct.put("title", "第" + (i + 1) + "幕");
                fixedAct.put("scenes", new ArrayList<>());
                acts.set(i, fixedAct);
                act = fixedAct;
            }

            // Fix act_id type
            fixIntegerField(act, "act_id", i + 1);

            // Ensure scenes
            if (!act.containsKey("scenes") || !(act.get("scenes") instanceof List)) {
                act.put("scenes", new ArrayList<>());
            }
            List<Map<String, Object>> scenes = (List<Map<String, Object>>) act.get("scenes");
            if (scenes.isEmpty()) {
                Map<String, Object> defaultScene = new LinkedHashMap<>();
                defaultScene.put("scene_id", 1);
                defaultScene.put("setting", "场景");
                defaultScene.put("beats", new ArrayList<>());
                scenes.add(defaultScene);
            }

            // Fix each scene
            for (int j = 0; j < scenes.size(); j++) {
                Map<String, Object> scene = scenes.get(j);
                if (!(scene instanceof Map)) continue;

                fixIntegerField(scene, "scene_id", j + 1);

                if (!scene.containsKey("setting") || scene.get("setting") == null) {
                    scene.put("setting", "场景");
                }

                if (!scene.containsKey("beats") || !(scene.get("beats") instanceof List)) {
                    scene.put("beats", new ArrayList<>());
                }

                List<Map<String, Object>> beats = (List<Map<String, Object>>) scene.get("beats");
                for (int k = 0; k < beats.size(); k++) {
                    Map<String, Object> beat = beats.get(k);
                    if (!(beat instanceof Map)) continue;

                    fixIntegerField(beat, "beat_id", k + 1);

                    // Fix dialogues
                    if (beat.containsKey("dialogues") && beat.get("dialogues") instanceof List) {
                        List<Map<String, Object>> dialogues = (List<Map<String, Object>>) beat.get("dialogues");
                        for (Map<String, Object> dialogue : dialogues) {
                            if (!(dialogue instanceof Map)) continue;
                            if (!dialogue.containsKey("speaker") || dialogue.get("speaker") == null) {
                                dialogue.put("speaker", "未知角色");
                            }
                            if (!dialogue.containsKey("text") || dialogue.get("text") == null) {
                                dialogue.put("text", "");
                            }
                        }
                    }

                    // Ensure stage_directions is a list
                    if (beat.containsKey("stage_directions") && !(beat.get("stage_directions") instanceof List)) {
                        beat.put("stage_directions", List.of(String.valueOf(beat.get("stage_directions"))));
                    }
                }
            }
        }

        // Ensure characters is a list if present
        if (scriptNode.containsKey("characters") && !(scriptNode.get("characters") instanceof List)) {
            scriptNode.put("characters", new ArrayList<>());
        }

        // Ensure locations is a list if present
        if (scriptNode.containsKey("locations") && !(scriptNode.get("locations") instanceof List)) {
            scriptNode.put("locations", new ArrayList<>());
        }

        // Dump fixed YAML
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setAllowUnicode(true);
        options.setIndent(2);
        options.setSplitLines(false);

        Yaml dumper = new Yaml(options);
        return dumper.dump(parsed);
    }

    // ---- Private validation helpers ----

    @SuppressWarnings("unchecked")
    private void validateScriptNode(Map<String, Object> scriptNode,
                                     List<Map<String, String>> errors,
                                     List<Map<String, String>> warnings) {
        // Validate metadata
        if (!scriptNode.containsKey("metadata")) {
            errors.add(errorEntry("script.metadata", "缺少必需字段: metadata"));
        } else {
            Object metadataObj = scriptNode.get("metadata");
            if (!(metadataObj instanceof Map)) {
                errors.add(errorEntry("script.metadata", "metadata 必须是对象类型"));
            } else {
                Map<String, Object> metadata = (Map<String, Object>) metadataObj;
                if (!metadata.containsKey("title") || metadata.get("title") == null) {
                    errors.add(errorEntry("script.metadata.title", "缺少必需字段: title"));
                }
                if (!metadata.containsKey("source_chapters")) {
                    warnings.add(warningEntry("script.metadata.source_chapters", "建议包含 source_chapters 字段"));
                }
            }
        }

        // Validate acts
        if (!scriptNode.containsKey("acts")) {
            errors.add(errorEntry("script.acts", "缺少必需字段: acts"));
            return;
        }

        Object actsObj = scriptNode.get("acts");
        if (!(actsObj instanceof List)) {
            errors.add(errorEntry("script.acts", "acts 必须是数组类型"));
            return;
        }

        List<Map<String, Object>> acts = (List<Map<String, Object>>) actsObj;
        if (acts.isEmpty()) {
            errors.add(errorEntry("script.acts", "acts 数组不能为空"));
            return;
        }

        for (int i = 0; i < acts.size(); i++) {
            String actPath = "script.acts[" + i + "]";
            Object actObj = acts.get(i);
            if (!(actObj instanceof Map)) {
                errors.add(errorEntry(actPath, "act 必须是对象类型"));
                continue;
            }
            Map<String, Object> act = (Map<String, Object>) actObj;
            validateAct(act, actPath, errors, warnings);
        }

        // Validate characters if present
        if (scriptNode.containsKey("characters")) {
            Object charsObj = scriptNode.get("characters");
            if (!(charsObj instanceof List)) {
                warnings.add(warningEntry("script.characters", "characters 应为数组类型"));
            } else {
                List<?> charsList = (List<?>) charsObj;
                for (int i = 0; i < charsList.size(); i++) {
                    Object charObj = charsList.get(i);
                    if (charObj instanceof Map) {
                        Map<String, Object> character = (Map<String, Object>) charObj;
                        if (!character.containsKey("name") || character.get("name") == null) {
                            errors.add(errorEntry("script.characters[" + i + "].name",
                                    "角色缺少必需字段: name"));
                        }
                    }
                }
            }
        }

        // Validate locations if present
        if (scriptNode.containsKey("locations")) {
            Object locsObj = scriptNode.get("locations");
            if (!(locsObj instanceof List)) {
                warnings.add(warningEntry("script.locations", "locations 应为数组类型"));
            } else {
                List<?> locsList = (List<?>) locsObj;
                for (int i = 0; i < locsList.size(); i++) {
                    Object locObj = locsList.get(i);
                    if (locObj instanceof Map) {
                        Map<String, Object> location = (Map<String, Object>) locObj;
                        if (!location.containsKey("name") || location.get("name") == null) {
                            errors.add(errorEntry("script.locations[" + i + "].name",
                                    "地点缺少必需字段: name"));
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateAct(Map<String, Object> act, String path,
                              List<Map<String, String>> errors,
                              List<Map<String, String>> warnings) {
        if (!act.containsKey("act_id")) {
            errors.add(errorEntry(path + ".act_id", "缺少必需字段: act_id"));
        }

        if (!act.containsKey("scenes")) {
            errors.add(errorEntry(path + ".scenes", "缺少必需字段: scenes"));
            return;
        }

        Object scenesObj = act.get("scenes");
        if (!(scenesObj instanceof List)) {
            errors.add(errorEntry(path + ".scenes", "scenes 必须是数组类型"));
            return;
        }

        List<Map<String, Object>> scenes = (List<Map<String, Object>>) scenesObj;
        if (scenes.isEmpty()) {
            errors.add(errorEntry(path + ".scenes", "scenes 数组不能为空"));
            return;
        }

        for (int j = 0; j < scenes.size(); j++) {
            String scenePath = path + ".scenes[" + j + "]";
            Object sceneObj = scenes.get(j);
            if (!(sceneObj instanceof Map)) {
                errors.add(errorEntry(scenePath, "scene 必须是对象类型"));
                continue;
            }
            Map<String, Object> scene = (Map<String, Object>) sceneObj;
            validateScene(scene, scenePath, errors, warnings);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateScene(Map<String, Object> scene, String path,
                                List<Map<String, String>> errors,
                                List<Map<String, String>> warnings) {
        if (!scene.containsKey("scene_id")) {
            errors.add(errorEntry(path + ".scene_id", "缺少必需字段: scene_id"));
        }

        if (!scene.containsKey("setting")) {
            errors.add(errorEntry(path + ".setting", "缺少必需字段: setting"));
        }

        if (!scene.containsKey("beats")) {
            errors.add(errorEntry(path + ".beats", "缺少必需字段: beats"));
            return;
        }

        Object beatsObj = scene.get("beats");
        if (!(beatsObj instanceof List)) {
            errors.add(errorEntry(path + ".beats", "beats 必须是数组类型"));
            return;
        }

        List<Map<String, Object>> beats = (List<Map<String, Object>>) beatsObj;
        if (beats.isEmpty()) {
            warnings.add(warningEntry(path + ".beats", "beats 数组为空，建议添加节拍内容"));
        }

        for (int k = 0; k < beats.size(); k++) {
            String beatPath = path + ".beats[" + k + "]";
            Object beatObj = beats.get(k);
            if (!(beatObj instanceof Map)) {
                errors.add(errorEntry(beatPath, "beat 必须是对象类型"));
                continue;
            }
            Map<String, Object> beat = (Map<String, Object>) beatObj;
            validateBeat(beat, beatPath, errors, warnings);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateBeat(Map<String, Object> beat, String path,
                               List<Map<String, String>> errors,
                               List<Map<String, String>> warnings) {
        if (!beat.containsKey("beat_id")) {
            errors.add(errorEntry(path + ".beat_id", "缺少必需字段: beat_id"));
        }

        // Validate dialogues if present
        if (beat.containsKey("dialogues")) {
            Object dialoguesObj = beat.get("dialogues");
            if (!(dialoguesObj instanceof List)) {
                errors.add(errorEntry(path + ".dialogues", "dialogues 必须是数组类型"));
            } else {
                List<Map<String, Object>> dialogues = (List<Map<String, Object>>) dialoguesObj;
                for (int d = 0; d < dialogues.size(); d++) {
                    String dialoguePath = path + ".dialogues[" + d + "]";
                    Object dialogueObj = dialogues.get(d);
                    if (!(dialogueObj instanceof Map)) {
                        errors.add(errorEntry(dialoguePath, "dialogue 必须是对象类型"));
                        continue;
                    }
                    Map<String, Object> dialogue = (Map<String, Object>) dialogueObj;
                    if (!dialogue.containsKey("speaker") || dialogue.get("speaker") == null) {
                        errors.add(errorEntry(dialoguePath + ".speaker", "对话缺少必需字段: speaker"));
                    }
                    if (!dialogue.containsKey("text") || dialogue.get("text") == null) {
                        errors.add(errorEntry(dialoguePath + ".text", "对话缺少必需字段: text"));
                    }
                }
            }
        }

        // Validate stage_directions if present
        if (beat.containsKey("stage_directions")) {
            Object dirsObj = beat.get("stage_directions");
            if (!(dirsObj instanceof List)) {
                warnings.add(warningEntry(path + ".stage_directions", "stage_directions 应为数组类型"));
            }
        }

        // Validate source_mapping if present
        if (beat.containsKey("source_mapping")) {
            Object mappingObj = beat.get("source_mapping");
            if (!(mappingObj instanceof Map)) {
                warnings.add(warningEntry(path + ".source_mapping", "source_mapping 应为对象类型"));
            } else {
                Map<String, Object> mapping = (Map<String, Object>) mappingObj;
                if (!mapping.containsKey("source_chapter")) {
                    warnings.add(warningEntry(path + ".source_mapping.source_chapter",
                            "建议包含 source_chapter 字段"));
                }
                if (!mapping.containsKey("confidence")) {
                    warnings.add(warningEntry(path + ".source_mapping.confidence",
                            "建议包含 confidence 字段"));
                }
            }
        }
    }

    // ---- Utility methods ----

    private void fixIntegerField(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            map.put(key, defaultValue);
        } else if (value instanceof String) {
            try {
                map.put(key, Integer.parseInt((String) value));
            } catch (NumberFormatException e) {
                map.put(key, defaultValue);
            }
        } else if (!(value instanceof Number)) {
            map.put(key, defaultValue);
        }
    }

    private Map<String, String> errorEntry(String path, String message) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("path", path);
        entry.put("message", message);
        entry.put("level", "error");
        return entry;
    }

    private Map<String, String> warningEntry(String path, String message) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("path", path);
        entry.put("message", message);
        entry.put("level", "warning");
        return entry;
    }

    private String buildMinimalValidYaml() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> script = new LinkedHashMap<>();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", "未命名剧本");
        script.put("metadata", metadata);

        Map<String, Object> scene = new LinkedHashMap<>();
        scene.put("scene_id", 1);
        scene.put("setting", "默认场景");
        scene.put("beats", new ArrayList<>());

        Map<String, Object> act = new LinkedHashMap<>();
        act.put("act_id", 1);
        act.put("title", "第一幕");
        act.put("scenes", List.of(scene));

        script.put("acts", List.of(act));
        root.put("script", script);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setAllowUnicode(true);
        options.setIndent(2);
        options.setSplitLines(false);

        Yaml dumper = new Yaml(options);
        return dumper.dump(root);
    }
}
