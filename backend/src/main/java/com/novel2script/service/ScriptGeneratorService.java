package com.novel2script.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.dto.ScriptResponse;
import com.novel2script.entity.Analysis;
import com.novel2script.entity.Chapter;
import com.novel2script.entity.Project;
import com.novel2script.entity.Script;
import com.novel2script.exception.GlobalExceptionHandler.BadRequestException;
import com.novel2script.exception.GlobalExceptionHandler.ResourceNotFoundException;
import com.novel2script.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptGeneratorService {

    private final DeepSeekService deepSeekService;
    private final TextProcessorService textProcessorService;
    private final ChapterRepository chapterRepository;
    private final AnalysisRepository analysisRepository;
    private final ScriptRepository scriptRepository;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Main entry point: generate a complete screenplay for a project.
     * Runs the full pipeline: analysis -> per-chapter screenplay generation -> assembly -> save.
     */
    @Transactional
    public ScriptResponse generateScript(Long projectId) {
        log.info("开始为项目 {} 生成剧本", projectId);

        // 1. Load project and all chapters
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("项目不存在: " + projectId));

        List<Chapter> chapters = chapterRepository.findByProjectIdOrderByChapterNumberAsc(projectId);
        if (chapters.isEmpty()) {
            throw new BadRequestException("项目没有章节内容，无法生成剧本");
        }

        // 2. Concatenate all chapter texts
        String allChaptersText = chapters.stream()
                .map(ch -> textProcessorService.preprocessChapter(ch.getContent()))
                .collect(Collectors.joining("\n\n"));

        log.debug("合并全部章节文本，总长度: {}", allChaptersText.length());

        // 3. Extract characters from full text
        List<Map<String, Object>> characters = deepSeekService.extractCharacters(allChaptersText);
        String charactersJson = toJson(characters);

        // 4. Extract locations from full text
        List<Map<String, Object>> locations = deepSeekService.extractLocations(allChaptersText);
        String locationsJson = toJson(locations);

        // 5. Extract events and generate summaries per chapter
        List<Map<String, Object>> allEvents = new ArrayList<>();
        List<Map<String, Object>> chapterSummaries = new ArrayList<>();

        for (Chapter chapter : chapters) {
            String chapterText = textProcessorService.preprocessChapter(chapter.getContent());

            // Generate chapter summary
            String summary = deepSeekService.generateChapterSummary(chapterText, chapter.getChapterNumber());
            Map<String, Object> summaryEntry = new LinkedHashMap<>();
            summaryEntry.put("chapter", chapter.getChapterNumber());
            summaryEntry.put("summary", summary);
            chapterSummaries.add(summaryEntry);

            // Extract events for this chapter
            List<Map<String, Object>> events = deepSeekService.extractEvents(chapterText, chapter.getChapterNumber());
            allEvents.addAll(events);
        }

        String eventsJson = toJson(allEvents);
        String summariesJson = toJson(chapterSummaries);

        // Save/update Analysis entity
        Analysis analysis = analysisRepository.findByProjectId(projectId).orElse(
                Analysis.builder().project(project).build()
        );
        analysis.setCharactersJson(charactersJson);
        analysis.setLocationsJson(locationsJson);
        analysis.setEventsJson(eventsJson);
        analysis.setChapterSummariesJson(summariesJson);
        analysisRepository.save(analysis);

        log.debug("分析数据已保存到数据库");

        // 6. Generate screenplay for each chapter
        List<String> chapterYamls = new ArrayList<>();
        String previousContext = "";

        for (Chapter chapter : chapters) {
            String chapterText = textProcessorService.preprocessChapter(chapter.getContent());
            log.info("正在生成第{}章剧本: {}", chapter.getChapterNumber(), chapter.getTitle());

            String chapterYaml = deepSeekService.generateScreenplay(
                    chapterText,
                    chapter.getChapterNumber(),
                    charactersJson,
                    locationsJson,
                    previousContext
            );
            chapterYamls.add(chapterYaml);

            // Use summary as context for the next chapter
            previousContext = chapterSummaries.stream()
                    .filter(s -> Objects.equals(s.get("chapter"), chapter.getChapterNumber()))
                    .map(s -> (String) s.get("summary"))
                    .findFirst()
                    .orElse("");
        }

        // 7. Assemble all chapter screenplays into one unified YAML
        List<Integer> sourceChapters = chapters.stream()
                .map(Chapter::getChapterNumber)
                .collect(Collectors.toList());

        String finalYaml = assembleYaml(
                project.getTitle(),
                project.getAuthor(),
                sourceChapters,
                charactersJson,
                locationsJson,
                chapterYamls
        );

        // 8. Create new Script entity (increment version if exists)
        int nextVersion = scriptRepository.findTopByProjectIdOrderByVersionDesc(projectId)
                .map(s -> s.getVersion() + 1)
                .orElse(1);

        Script script = Script.builder()
                .project(project)
                .version(nextVersion)
                .title(project.getTitle() + " - 剧本 v" + nextVersion)
                .contentYaml(finalYaml)
                .validationResultJson("{}")
                .build();

        script = scriptRepository.save(script);

        log.info("剧本已生成并保存，版本: {}, ID: {}", nextVersion, script.getId());

        // 9. Return ScriptResponse DTO
        return buildScriptResponse(script);
    }

    /**
     * Parse chapter YAMLs and merge into a final unified YAML structure
     * with metadata, characters, locations, and acts.
     */
    @SuppressWarnings("unchecked")
    public String assembleYaml(String title, String author, List<Integer> sourceChapters,
                                String charactersJson, String locationsJson,
                                List<String> chapterYamls) {
        log.debug("组装最终YAML，章节数: {}", chapterYamls.size());

        Yaml yaml = new Yaml();

        // Build the root structure
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> script = new LinkedHashMap<>();

        // Metadata
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", title);
        metadata.put("author", author != null ? author : "");
        metadata.put("source_chapters", sourceChapters);
        script.put("metadata", metadata);

        // Characters
        try {
            List<Map<String, Object>> characters = objectMapper.readValue(
                    charactersJson, new TypeReference<List<Map<String, Object>>>() {});
            script.put("characters", characters);
        } catch (Exception e) {
            log.warn("解析角色JSON失败，使用空列表: {}", e.getMessage());
            script.put("characters", Collections.emptyList());
        }

        // Locations
        try {
            List<Map<String, Object>> locations = objectMapper.readValue(
                    locationsJson, new TypeReference<List<Map<String, Object>>>() {});
            script.put("locations", locations);
        } catch (Exception e) {
            log.warn("解析地点JSON失败，使用空列表: {}", e.getMessage());
            script.put("locations", Collections.emptyList());
        }

        // Merge acts from all chapter YAMLs
        List<Map<String, Object>> allActs = new ArrayList<>();
        int actIdCounter = 1;
        int sceneIdCounter = 1;

        for (int i = 0; i < chapterYamls.size(); i++) {
            String chapterYaml = chapterYamls.get(i);
            try {
                Map<String, Object> parsed = yaml.load(chapterYaml);
                if (parsed == null) continue;

                // Try to extract acts from the parsed YAML
                List<Map<String, Object>> acts = null;

                // The chapter YAML might have acts at root level or under a key
                if (parsed.containsKey("acts")) {
                    acts = (List<Map<String, Object>>) parsed.get("acts");
                } else if (parsed.containsKey("script")) {
                    Map<String, Object> inner = (Map<String, Object>) parsed.get("script");
                    if (inner != null && inner.containsKey("acts")) {
                        acts = (List<Map<String, Object>>) inner.get("acts");
                    }
                }

                if (acts != null) {
                    for (Map<String, Object> act : acts) {
                        act.put("act_id", actIdCounter++);
                        // Re-number scenes within the act
                        List<Map<String, Object>> scenes = (List<Map<String, Object>>) act.get("scenes");
                        if (scenes != null) {
                            for (Map<String, Object> scene : scenes) {
                                scene.put("scene_id", sceneIdCounter++);
                            }
                        }
                        allActs.add(act);
                    }
                } else {
                    // If the chapter YAML is a flat structure, wrap it as a single act
                    Map<String, Object> act = new LinkedHashMap<>();
                    act.put("act_id", actIdCounter++);
                    act.put("title", "第" + sourceChapters.get(i) + "章");
                    if (parsed.containsKey("scenes")) {
                        act.put("scenes", parsed.get("scenes"));
                    } else {
                        // Create a minimal scene
                        Map<String, Object> scene = new LinkedHashMap<>();
                        scene.put("scene_id", sceneIdCounter++);
                        scene.put("setting", "第" + sourceChapters.get(i) + "章场景");
                        scene.put("beats", Collections.emptyList());
                        act.put("scenes", List.of(scene));
                    }
                    allActs.add(act);
                }

            } catch (Exception e) {
                log.warn("解析第{}章YAML失败: {}", i + 1, e.getMessage());
                // Create a placeholder act for this chapter
                Map<String, Object> act = new LinkedHashMap<>();
                act.put("act_id", actIdCounter++);
                act.put("title", "第" + sourceChapters.get(i) + "章");
                Map<String, Object> scene = new LinkedHashMap<>();
                scene.put("scene_id", sceneIdCounter++);
                scene.put("setting", "第" + sourceChapters.get(i) + "章场景");
                scene.put("beats", Collections.emptyList());
                act.put("scenes", List.of(scene));
                allActs.add(act);
            }
        }

        script.put("acts", allActs);
        root.put("script", script);

        // Dump to YAML string with nice formatting
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setAllowUnicode(true);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        options.setSplitLines(false);

        Yaml dumper = new Yaml(options);
        return dumper.dump(root);
    }

    // ---- Private helpers ----

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON序列化失败: {}", e.getMessage());
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private ScriptResponse buildScriptResponse(Script script) {
        Map<String, Object> validationResult = Collections.emptyMap();
        try {
            if (script.getValidationResultJson() != null && !script.getValidationResultJson().isBlank()) {
                validationResult = objectMapper.readValue(
                        script.getValidationResultJson(), new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            log.warn("解析验证结果JSON失败: {}", e.getMessage());
        }

        return ScriptResponse.builder()
                .id(script.getId())
                .projectId(script.getProject().getId())
                .version(script.getVersion())
                .title(script.getTitle())
                .contentYaml(script.getContentYaml())
                .validationResult(validationResult)
                .createdAt(script.getCreatedAt())
                .updatedAt(script.getUpdatedAt())
                .build();
    }
}
