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
     * Main entry point: generate a complete screenplay for a project (full mode).
     * Runs the full pipeline: analysis -> per-chapter screenplay generation -> assembly -> save.
     */
    @Transactional
    public ScriptResponse generateScript(Long projectId) {
        log.info("开始为项目 {} 生成剧本（全量模式）", projectId);

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

        // 5. Extract events and generate summaries per chapter, with per-chapter character/location analysis
        List<Map<String, Object>> allEvents = new ArrayList<>();
        List<Map<String, Object>> chapterSummaries = new ArrayList<>();
        List<Map<String, Object>> perChapterAnalysis = new ArrayList<>();
        Set<String> seenCharacterNames = new HashSet<>();
        Set<String> seenLocationNames = new HashSet<>();

        for (Chapter chapter : chapters) {
            String chapterText = textProcessorService.preprocessChapter(chapter.getContent());

            String summary = deepSeekService.generateChapterSummary(chapterText, chapter.getChapterNumber());
            Map<String, Object> summaryEntry = new LinkedHashMap<>();
            summaryEntry.put("chapter", chapter.getChapterNumber());
            summaryEntry.put("summary", summary);
            chapterSummaries.add(summaryEntry);

            List<Map<String, Object>> events = deepSeekService.extractEvents(chapterText, chapter.getChapterNumber());
            allEvents.addAll(events);

            // Per-chapter character and location extraction
            List<Map<String, Object>> chChars = deepSeekService.extractCharacters(chapterText);
            List<Map<String, Object>> chLocs = deepSeekService.extractLocations(chapterText);

            List<Map<String, Object>> newChars = computeNewByName(chChars, seenCharacterNames);
            List<Map<String, Object>> newLocs = computeNewByName(chLocs, seenLocationNames);

            for (Map<String, Object> c : chChars) {
                seenCharacterNames.add(String.valueOf(c.getOrDefault("name", "")));
            }
            for (Map<String, Object> l : chLocs) {
                seenLocationNames.add(String.valueOf(l.getOrDefault("name", "")));
            }

            Map<String, Object> chEntry = new LinkedHashMap<>();
            chEntry.put("chapter", chapter.getChapterNumber());
            chEntry.put("newCharacters", newChars);
            chEntry.put("newLocations", newLocs);
            chEntry.put("summary", summary);
            perChapterAnalysis.add(chEntry);
        }

        String eventsJson = toJson(allEvents);
        String summariesJson = toJson(chapterSummaries);
        String perChapterAnalysisJson = toJson(perChapterAnalysis);

        // Save/update Analysis entity
        Analysis analysis = analysisRepository.findByProjectId(projectId).orElse(
                Analysis.builder().project(project).build()
        );
        analysis.setCharactersJson(charactersJson);
        analysis.setLocationsJson(locationsJson);
        analysis.setEventsJson(eventsJson);
        analysis.setChapterSummariesJson(summariesJson);
        analysis.setPerChapterAnalysisJson(perChapterAnalysisJson);

        List<Integer> allChapterNumbers = chapters.stream()
                .map(Chapter::getChapterNumber)
                .collect(Collectors.toList());
        analysis.setAnalyzedChaptersJson(toJson(allChapterNumbers));
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

            previousContext = chapterSummaries.stream()
                    .filter(s -> Objects.equals(s.get("chapter"), chapter.getChapterNumber()))
                    .map(s -> (String) s.get("summary"))
                    .findFirst()
                    .orElse("");
        }

        // 7. Assemble all chapter screenplays into one unified YAML
        String finalYaml = assembleYaml(
                project.getTitle(),
                project.getAuthor(),
                allChapterNumbers,
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
                .title(project.getTitle() + " - " + formatChapterRange(allChapterNumbers))
                .contentYaml(finalYaml)
                .validationResultJson("{}")
                .build();

        script = scriptRepository.save(script);
        log.info("剧本已生成并保存，版本: {}, ID: {}", nextVersion, script.getId());

        return buildScriptResponse(script);
    }

    /**
     * Incremental mode: analyze and generate screenplay only for selected chapters,
     * then merge results with existing analysis and screenplay.
     */
    @Transactional
    public ScriptResponse generateIncremental(Long projectId, List<Integer> selectedChapterNumbers) {
        log.info("开始增量生成剧本，项目ID: {}, 选中章节: {}", projectId, selectedChapterNumbers);

        // 1. Load project
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("项目不存在: " + projectId));

        // 2. Load existing analysis (may be null for first run)
        Optional<Analysis> existingAnalysisOpt = analysisRepository.findByProjectId(projectId);
        Analysis existingAnalysis = existingAnalysisOpt.orElse(null);

        Set<Integer> existingAnalyzedChapters = new HashSet<>();
        List<Map<String, Object>> existingCharacters = new ArrayList<>();
        List<Map<String, Object>> existingLocations = new ArrayList<>();
        List<Map<String, Object>> existingEvents = new ArrayList<>();
        List<Map<String, Object>> existingSummaries = new ArrayList<>();

        if (existingAnalysis != null) {
            existingAnalyzedChapters = new HashSet<>(parseJsonIntArray(existingAnalysis.getAnalyzedChaptersJson()));
            existingCharacters = parseJsonArray(existingAnalysis.getCharactersJson());
            existingLocations = parseJsonArray(existingAnalysis.getLocationsJson());
            existingEvents = parseJsonArray(existingAnalysis.getEventsJson());
            existingSummaries = parseJsonArray(existingAnalysis.getChapterSummariesJson());
        }

        // 3. Load only the selected chapters
        List<Chapter> allProjectChapters = chapterRepository.findByProjectIdOrderByChapterNumberAsc(projectId);
        Map<Integer, Chapter> chapterMap = allProjectChapters.stream()
                .collect(Collectors.toMap(Chapter::getChapterNumber, ch -> ch));

        List<Chapter> selectedChapters = new ArrayList<>();
        for (int num : selectedChapterNumbers) {
            Chapter ch = chapterMap.get(num);
            if (ch == null) {
                throw new BadRequestException("章节不存在: 第" + num + "章");
            }
            selectedChapters.add(ch);
        }

        // Sort selected chapters by chapter number for consistent processing
        selectedChapters.sort(Comparator.comparingInt(Chapter::getChapterNumber));

        // 4. Extract characters and locations from selected chapters, then merge
        String selectedText = selectedChapters.stream()
                .map(ch -> textProcessorService.preprocessChapter(ch.getContent()))
                .collect(Collectors.joining("\n\n"));

        List<Map<String, Object>> newCharacters = deepSeekService.extractCharacters(selectedText);
        List<Map<String, Object>> newLocations = deepSeekService.extractLocations(selectedText);

        List<Map<String, Object>> mergedCharacters = mergeByName(existingCharacters, newCharacters);
        List<Map<String, Object>> mergedLocations = mergeByName(existingLocations, newLocations);

        String mergedCharactersJson = toJson(mergedCharacters);
        String mergedLocationsJson = toJson(mergedLocations);

        // 5. Generate summaries, extract events and per-chapter analysis for selected chapters
        List<Map<String, Object>> newSummaries = new ArrayList<>();
        List<Map<String, Object>> newEvents = new ArrayList<>();
        List<Map<String, Object>> newPerChapterAnalysis = new ArrayList<>();

        // Load existing per-chapter analysis
        List<Map<String, Object>> existingPerChapterAnalysis = new ArrayList<>();
        if (existingAnalysis != null) {
            existingPerChapterAnalysis = parseJsonArray(existingAnalysis.getPerChapterAnalysisJson());
        }

        // Collect all previously seen character/location names from existing per-chapter data
        Set<String> prevCharNames = new HashSet<>();
        Set<String> prevLocNames = new HashSet<>();
        for (Map<String, Object> entry : existingPerChapterAnalysis) {
            List<Map<String, Object>> nc = (List<Map<String, Object>>) entry.getOrDefault("newCharacters", Collections.emptyList());
            for (Map<String, Object> c : nc) {
                prevCharNames.add(String.valueOf(c.getOrDefault("name", "")));
            }
            List<Map<String, Object>> nl = (List<Map<String, Object>>) entry.getOrDefault("newLocations", Collections.emptyList());
            for (Map<String, Object> l : nl) {
                prevLocNames.add(String.valueOf(l.getOrDefault("name", "")));
            }
        }

        for (Chapter ch : selectedChapters) {
            String chapterText = textProcessorService.preprocessChapter(ch.getContent());

            String summary = deepSeekService.generateChapterSummary(chapterText, ch.getChapterNumber());
            Map<String, Object> summaryEntry = new LinkedHashMap<>();
            summaryEntry.put("chapter", ch.getChapterNumber());
            summaryEntry.put("summary", summary);
            newSummaries.add(summaryEntry);

            List<Map<String, Object>> events = deepSeekService.extractEvents(chapterText, ch.getChapterNumber());
            newEvents.addAll(events);

            // Per-chapter character and location extraction
            List<Map<String, Object>> chChars = deepSeekService.extractCharacters(chapterText);
            List<Map<String, Object>> chLocs = deepSeekService.extractLocations(chapterText);

            List<Map<String, Object>> newChars = computeNewByName(chChars, prevCharNames);
            List<Map<String, Object>> newLocs = computeNewByName(chLocs, prevLocNames);

            for (Map<String, Object> c : chChars) {
                prevCharNames.add(String.valueOf(c.getOrDefault("name", "")));
            }
            for (Map<String, Object> l : chLocs) {
                prevLocNames.add(String.valueOf(l.getOrDefault("name", "")));
            }

            Map<String, Object> chEntry = new LinkedHashMap<>();
            chEntry.put("chapter", ch.getChapterNumber());
            chEntry.put("newCharacters", newChars);
            chEntry.put("newLocations", newLocs);
            chEntry.put("summary", summary);
            newPerChapterAnalysis.add(chEntry);
        }

        // Merge summaries: keep existing for unselected chapters, replace for selected
        Set<Integer> selectedSet = new HashSet<>(selectedChapterNumbers);
        List<Map<String, Object>> mergedSummaries = new ArrayList<>(existingSummaries);
        mergedSummaries.removeIf(s -> selectedSet.contains(toInt(s.get("chapter"))));
        mergedSummaries.addAll(newSummaries);
        mergedSummaries.sort(Comparator.comparingInt(s -> toInt(s.get("chapter"))));

        // Merge events: remove old events for selected chapters, append new
        List<Map<String, Object>> mergedEvents = new ArrayList<>(existingEvents);
        mergedEvents.removeIf(e -> selectedSet.contains(toInt(e.get("chapter"))));
        mergedEvents.addAll(newEvents);

        // Merge per-chapter analysis: replace for selected chapters, keep others
        List<Map<String, Object>> mergedPerChapterAnalysis = new ArrayList<>(existingPerChapterAnalysis);
        mergedPerChapterAnalysis.removeIf(e -> selectedSet.contains(toInt(e.get("chapter"))));
        mergedPerChapterAnalysis.addAll(newPerChapterAnalysis);
        mergedPerChapterAnalysis.sort(Comparator.comparingInt(e -> toInt(e.get("chapter"))));

        // 6. Save merged analysis
        Set<Integer> updatedAnalyzedChapters = new HashSet<>(existingAnalyzedChapters);
        updatedAnalyzedChapters.addAll(selectedChapterNumbers);

        Analysis analysis = existingAnalysis != null ? existingAnalysis
                : Analysis.builder().project(project).build();
        analysis.setCharactersJson(mergedCharactersJson);
        analysis.setLocationsJson(mergedLocationsJson);
        analysis.setEventsJson(toJson(mergedEvents));
        analysis.setChapterSummariesJson(toJson(mergedSummaries));
        analysis.setPerChapterAnalysisJson(toJson(mergedPerChapterAnalysis));
        analysis.setAnalyzedChaptersJson(toJson(new ArrayList<>(updatedAnalyzedChapters)));
        analysisRepository.save(analysis);

        log.debug("增量分析数据已合并并保存到数据库");

        // 7. Generate screenplay for selected chapters (with context from previous chapters)
        List<String> chapterYamls = new ArrayList<>();
        List<Integer> generatedChapterNums = new ArrayList<>();

        for (Chapter ch : selectedChapters) {
            String chapterText = textProcessorService.preprocessChapter(ch.getContent());
            log.info("增量生成第{}章剧本: {}", ch.getChapterNumber(), ch.getTitle());

            // Find previous chapter's summary for continuity
            String previousContext = findPreviousContext(
                    ch.getChapterNumber(), mergedSummaries, allProjectChapters);

            String chapterYaml = deepSeekService.generateScreenplay(
                    chapterText,
                    ch.getChapterNumber(),
                    mergedCharactersJson,
                    mergedLocationsJson,
                    previousContext
            );
            chapterYamls.add(chapterYaml);
            generatedChapterNums.add(ch.getChapterNumber());
        }

        // 8. Assemble or merge screenplay
        String existingYaml = scriptRepository.findTopByProjectIdOrderByVersionDesc(projectId)
                .map(Script::getContentYaml)
                .orElse(null);

        String finalYaml;
        if (existingYaml != null && !existingYaml.isBlank() && existingAnalysis != null) {
            // Incremental merge: only keep selected chapters' acts
            finalYaml = mergeScreenplayYaml(
                    existingYaml,
                    project.getTitle(),
                    project.getAuthor(),
                    mergedCharactersJson,
                    mergedLocationsJson,
                    chapterYamls,
                    generatedChapterNums
            );

            // Safety check: verify new chapters' acts are present
            boolean hasNewChapterActs = false;
            for (int chNum : generatedChapterNums) {
                if (finalYaml.contains("第" + chNum + "章") || finalYaml.contains("source_chapters:\n    - " + chNum)) {
                    hasNewChapterActs = true;
                    break;
                }
            }
            if (!hasNewChapterActs) {
                log.warn("合并后未检测到新章节的幕，回退到全量组装");
                List<Integer> allChapterNums = allProjectChapters.stream()
                        .map(Chapter::getChapterNumber)
                        .collect(Collectors.toList());
                // Re-generate screenplay for ALL chapters to ensure completeness
                List<String> allChapterYamls = new ArrayList<>();
                String prevCtx = "";
                for (Chapter ch : allProjectChapters) {
                    String chText = textProcessorService.preprocessChapter(ch.getContent());
                    String chYaml = deepSeekService.generateScreenplay(
                            chText, ch.getChapterNumber(),
                            mergedCharactersJson, mergedLocationsJson, prevCtx);
                    allChapterYamls.add(chYaml);
                    prevCtx = mergedSummaries.stream()
                            .filter(s -> toInt(s.get("chapter")) == ch.getChapterNumber())
                            .map(s -> String.valueOf(s.getOrDefault("summary", "")))
                            .findFirst().orElse("");
                }
                finalYaml = assembleYaml(
                        project.getTitle(), project.getAuthor(),
                        allChapterNums, mergedCharactersJson, mergedLocationsJson,
                        allChapterYamls);
            }
        } else {
            // First time: assemble from scratch
            List<Integer> allChapterNums = allProjectChapters.stream()
                    .map(Chapter::getChapterNumber)
                    .collect(Collectors.toList());
            finalYaml = assembleYaml(
                    project.getTitle(),
                    project.getAuthor(),
                    allChapterNums,
                    mergedCharactersJson,
                    mergedLocationsJson,
                    chapterYamls
            );
        }

        // 9. Save script
        int nextVersion = scriptRepository.findTopByProjectIdOrderByVersionDesc(projectId)
                .map(s -> s.getVersion() + 1)
                .orElse(1);

        Script script = Script.builder()
                .project(project)
                .version(nextVersion)
                .title(project.getTitle() + " - " + formatChapterRange(selectedChapterNumbers))
                .contentYaml(finalYaml)
                .validationResultJson("{}")
                .build();

        script = scriptRepository.save(script);
        log.info("增量剧本已生成并保存，版本: {}, ID: {}", nextVersion, script.getId());

        return buildScriptResponse(script);
    }

    // ==================== YAML Assembly ====================

    /**
     * Parse chapter YAMLs and merge into a final unified YAML structure.
     * Each act is tagged with source_chapters for later incremental merging.
     */
    @SuppressWarnings("unchecked")
    public String assembleYaml(String title, String author, List<Integer> sourceChapters,
                                String charactersJson, String locationsJson,
                                List<String> chapterYamls) {
        log.debug("组装最终YAML，章节数: {}", chapterYamls.size());

        Yaml yaml = new Yaml();

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
            int chapterNum = (i < sourceChapters.size()) ? sourceChapters.get(i) : (i + 1);

            try {
                Map<String, Object> parsed = yaml.load(chapterYaml);
                if (parsed == null) continue;

                List<Map<String, Object>> acts = null;

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
                        act.put("source_chapters", List.of(chapterNum));
                        List<Map<String, Object>> scenes = (List<Map<String, Object>>) act.get("scenes");
                        if (scenes != null) {
                            for (Map<String, Object> scene : scenes) {
                                scene.put("scene_id", sceneIdCounter++);
                                scene.put("source_chapter", chapterNum);
                            }
                        }
                        allActs.add(act);
                    }
                } else {
                    Map<String, Object> act = new LinkedHashMap<>();
                    act.put("act_id", actIdCounter++);
                    act.put("title", "第" + chapterNum + "章");
                    act.put("source_chapters", List.of(chapterNum));
                    if (parsed.containsKey("scenes")) {
                        act.put("scenes", parsed.get("scenes"));
                    } else {
                        Map<String, Object> scene = new LinkedHashMap<>();
                        scene.put("scene_id", sceneIdCounter++);
                        scene.put("setting", "第" + chapterNum + "章场景");
                        scene.put("beats", Collections.emptyList());
                        act.put("scenes", List.of(scene));
                    }
                    allActs.add(act);
                }

            } catch (Exception e) {
                log.warn("解析第{}章YAML失败: {}", i + 1, e.getMessage());
                Map<String, Object> act = new LinkedHashMap<>();
                act.put("act_id", actIdCounter++);
                act.put("title", "第" + chapterNum + "章");
                act.put("source_chapters", List.of(chapterNum));
                Map<String, Object> scene = new LinkedHashMap<>();
                scene.put("scene_id", sceneIdCounter++);
                scene.put("setting", "第" + chapterNum + "章场景");
                scene.put("beats", Collections.emptyList());
                act.put("scenes", List.of(scene));
                allActs.add(act);
            }
        }

        script.put("acts", allActs);
        root.put("script", script);

        return dumpYaml(root);
    }

    /**
     * Merge new chapter screenplays into an existing screenplay YAML.
     * Replaces acts for selected chapters while keeping existing acts for other chapters.
     */
    @SuppressWarnings("unchecked")
    public String mergeScreenplayYaml(String existingYaml, String title, String author,
                                       String charactersJson, String locationsJson,
                                       List<String> newChapterYamls,
                                       List<Integer> newChapterNumbers) {
        log.debug("合并剧本YAML，新章节: {}", newChapterNumbers);

        Yaml yaml = new Yaml();
        Set<Integer> newChapterSet = new HashSet<>(newChapterNumbers);

        // Parse existing screenplay
        Map<String, Object> existingRoot;
        try {
            existingRoot = yaml.load(existingYaml);
        } catch (Exception e) {
            log.warn("解析现有剧本YAML失败，回退到全量组装: {}", e.getMessage());
            return assembleYaml(title, author, newChapterNumbers, charactersJson, locationsJson, newChapterYamls);
        }

        if (existingRoot == null) {
            return assembleYaml(title, author, newChapterNumbers, charactersJson, locationsJson, newChapterYamls);
        }

        Map<String, Object> existingScript = (Map<String, Object>) existingRoot.get("script");
        if (existingScript == null) {
            existingScript = new LinkedHashMap<>();
        }

        // Parse new chapter YAMLs into acts
        Map<Integer, List<Map<String, Object>>> newActsByChapter = new LinkedHashMap<>();
        for (int i = 0; i < newChapterYamls.size(); i++) {
            int chapterNum = newChapterNumbers.get(i);
            String chapterYaml = newChapterYamls.get(i);
            List<Map<String, Object>> chapterActs = new ArrayList<>();

            try {
                Map<String, Object> parsed = yaml.load(chapterYaml);
                if (parsed != null) {
                    List<Map<String, Object>> acts = null;
                    if (parsed.containsKey("acts")) {
                        acts = (List<Map<String, Object>>) parsed.get("acts");
                    } else if (parsed.containsKey("script")) {
                        Map<String, Object> inner = (Map<String, Object>) parsed.get("script");
                        if (inner != null && inner.containsKey("acts")) {
                            acts = (List<Map<String, Object>>) inner.get("acts");
                        }
                    }
                    if (acts != null && !acts.isEmpty()) {
                        chapterActs.addAll(acts);
                    } else if (parsed.containsKey("scenes")) {
                        Map<String, Object> act = new LinkedHashMap<>();
                        act.put("title", "第" + chapterNum + "章");
                        act.put("scenes", parsed.get("scenes"));
                        chapterActs.add(act);
                    } else {
                        // Fallback: treat the entire parsed YAML as one act
                        Map<String, Object> act = new LinkedHashMap<>();
                        act.put("title", "第" + chapterNum + "章");
                        act.put("scenes", parsed.containsKey("scenes") ? parsed.get("scenes") : Collections.emptyList());
                        if (parsed.containsKey("beats")) {
                            Map<String, Object> scene = new LinkedHashMap<>();
                            scene.put("scene_id", 1);
                            scene.put("setting", "第" + chapterNum + "章场景");
                            scene.put("beats", parsed.get("beats"));
                            act.put("scenes", List.of(scene));
                        }
                        chapterActs.add(act);
                        log.info("第{}章YAML使用fallback包装为单幕", chapterNum);
                    }
                }
            } catch (Exception e) {
                log.warn("解析第{}章新YAML失败，创建占位幕: {}", chapterNum, e.getMessage());
                Map<String, Object> act = new LinkedHashMap<>();
                act.put("title", "第" + chapterNum + "章");
                Map<String, Object> scene = new LinkedHashMap<>();
                scene.put("scene_id", 1);
                scene.put("setting", "第" + chapterNum + "章场景");
                scene.put("beats", Collections.emptyList());
                act.put("scenes", List.of(scene));
                chapterActs.add(act);
            }

            // Tag each act and scene with source_chapters / source_chapter
            for (Map<String, Object> act : chapterActs) {
                act.put("source_chapters", List.of(chapterNum));
                List<Map<String, Object>> actScenes = (List<Map<String, Object>>) act.get("scenes");
                if (actScenes != null) {
                    for (Map<String, Object> sc : actScenes) {
                        sc.put("source_chapter", chapterNum);
                    }
                }
            }

            newActsByChapter.put(chapterNum, chapterActs);
        }

        // Only use newly generated acts for selected chapters — discard all existing acts
        List<Integer> sortedNewChapters = new ArrayList<>(newChapterNumbers);
        Collections.sort(sortedNewChapters);

        List<Map<String, Object>> allActs = new ArrayList<>();
        for (int chapterNum : sortedNewChapters) {
            List<Map<String, Object>> acts = newActsByChapter.getOrDefault(chapterNum, Collections.emptyList());
            allActs.addAll(acts);
        }

        log.info("增量合并完成，仅保留选中章节 {} 的 {} 个幕", sortedNewChapters, allActs.size());

        // Re-number act_id and scene_id
        int actIdCounter = 1;
        int sceneIdCounter = 1;
        for (Map<String, Object> act : allActs) {
            act.put("act_id", actIdCounter++);
            List<Map<String, Object>> scenes = (List<Map<String, Object>>) act.get("scenes");
            if (scenes != null) {
                for (Map<String, Object> scene : scenes) {
                    scene.put("scene_id", sceneIdCounter++);
                }
            }
        }

        // Update metadata
        Map<String, Object> metadata = (Map<String, Object>) existingScript.get("metadata");
        if (metadata == null) {
            metadata = new LinkedHashMap<>();
        }
        metadata.put("title", title);
        metadata.put("author", author != null ? author : "");
        // source_chapters reflects only the chapters in this screenplay (the selected ones)
        metadata.put("source_chapters", sortedNewChapters);

        // Update characters and locations in the script
        try {
            List<Map<String, Object>> characters = objectMapper.readValue(
                    charactersJson, new TypeReference<List<Map<String, Object>>>() {});
            existingScript.put("characters", characters);
        } catch (Exception e) {
            log.warn("更新角色信息失败: {}", e.getMessage());
        }

        try {
            List<Map<String, Object>> locations = objectMapper.readValue(
                    locationsJson, new TypeReference<List<Map<String, Object>>>() {});
            existingScript.put("locations", locations);
        } catch (Exception e) {
            log.warn("更新地点信息失败: {}", e.getMessage());
        }

        existingScript.put("metadata", metadata);
        existingScript.put("acts", allActs);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("script", existingScript);

        return dumpYaml(root);
    }

    // ==================== Merge Helpers ====================

    /**
     * Merge two lists of named entities (characters/locations) by "name" field.
     * New entries with the same name update existing entries; unique names are added.
     */
    private List<Map<String, Object>> mergeByName(List<Map<String, Object>> existing,
                                                    List<Map<String, Object>> newItems) {
        Map<String, Map<String, Object>> nameMap = new LinkedHashMap<>();
        for (Map<String, Object> item : existing) {
            String name = String.valueOf(item.getOrDefault("name", ""));
            nameMap.put(name, new LinkedHashMap<>(item));
        }
        for (Map<String, Object> item : newItems) {
            String name = String.valueOf(item.getOrDefault("name", ""));
            if (!name.isEmpty()) {
                nameMap.put(name, new LinkedHashMap<>(item));
            }
        }
        return new ArrayList<>(nameMap.values());
    }

    /**
     * Filter a list of named entities, returning only those whose "name" is not in the seenNames set.
     * Used to compute "new" characters/locations for per-chapter analysis.
     */
    private List<Map<String, Object>> computeNewByName(List<Map<String, Object>> items, Set<String> seenNames) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String name = String.valueOf(item.getOrDefault("name", ""));
            if (!name.isEmpty() && !seenNames.contains(name)) {
                result.add(new LinkedHashMap<>(item));
            }
        }
        return result;
    }

    /**
     * Find the summary of the chapter immediately before the given chapter number
     * from the merged summaries list. This ensures continuity for incremental generation.
     */
    private String findPreviousContext(int chapterNumber,
                                        List<Map<String, Object>> allSummaries,
                                        List<Chapter> allChapters) {
        // Find the chapter number just before the current one
        List<Integer> allNums = allChapters.stream()
                .map(Chapter::getChapterNumber)
                .sorted()
                .collect(Collectors.toList());

        int previousChapterNum = -1;
        for (int num : allNums) {
            if (num < chapterNumber) {
                previousChapterNum = num;
            } else {
                break;
            }
        }

        if (previousChapterNum == -1) {
            return "";
        }

        final int targetChapter = previousChapterNum;
        return allSummaries.stream()
                .filter(s -> toInt(s.get("chapter")) == targetChapter)
                .map(s -> String.valueOf(s.getOrDefault("summary", "")))
                .findFirst()
                .orElse("");
    }

    // ==================== Utility Helpers ====================

    /**
     * Format a list of chapter numbers into a readable range string.
     * e.g. [4,5,6] -> "章节4-6", [1,3,5] -> "章节1,3,5", [2] -> "章节2"
     */
    private String formatChapterRange(List<Integer> chapters) {
        if (chapters == null || chapters.isEmpty()) return "";
        List<Integer> sorted = new ArrayList<>(chapters);
        Collections.sort(sorted);
        if (sorted.size() == 1) return "章节" + sorted.get(0);
        // Check if it's a continuous range
        boolean continuous = true;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i) - sorted.get(i - 1) != 1) {
                continuous = false;
                break;
            }
        }
        if (continuous) {
            return "章节" + sorted.get(0) + "-" + sorted.get(sorted.size() - 1);
        } else {
            return "章节" + sorted.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON序列化失败: {}", e.getMessage());
            return "[]";
        }
    }

    private List<Map<String, Object>> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("解析JSON数组失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Integer> parseJsonIntArray(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Integer>>() {});
        } catch (Exception e) {
            log.warn("解析整数JSON数组失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private int toInt(Object obj) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (Exception e) {
            return -1;
        }
    }

    private String dumpYaml(Map<String, Object> root) {
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
