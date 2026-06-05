package com.novel2script.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.dto.*;
import com.novel2script.entity.Analysis;
import com.novel2script.entity.Script;
import com.novel2script.exception.GlobalExceptionHandler.BadRequestException;
import com.novel2script.exception.GlobalExceptionHandler.ResourceNotFoundException;
import com.novel2script.repository.AnalysisRepository;
import com.novel2script.repository.ScriptRepository;
import com.novel2script.service.SchemaValidatorService;
import com.novel2script.service.ScriptGeneratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/scripts")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptRepository scriptRepository;
    private final AnalysisRepository analysisRepository;
    private final ScriptGeneratorService scriptGeneratorService;
    private final SchemaValidatorService schemaValidatorService;
    private final ObjectMapper objectMapper;

    // ==================== Script Endpoints ====================

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public ScriptResponse generateScript(@RequestBody Map<String, Long> body) {
        Long projectId = body.get("projectId");
        if (projectId == null) {
            throw new BadRequestException("projectId 不能为空");
        }
        log.info("生成剧本，项目ID: {}", projectId);
        return scriptGeneratorService.generateScript(projectId);
    }

    @GetMapping("/project/{projectId}")
    public List<ScriptResponse> listScriptsByProject(@PathVariable Long projectId) {
        log.debug("获取项目 {} 的剧本列表", projectId);
        return scriptRepository.findByProjectIdOrderByVersionDesc(projectId).stream()
                .map(this::toScriptResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ScriptResponse getScript(@PathVariable Long id) {
        log.debug("获取剧本: {}", id);
        Script script = scriptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("剧本不存在: " + id));
        return toScriptResponse(script);
    }

    @PutMapping("/{id}")
    public ScriptResponse updateScript(@PathVariable Long id,
                                        @Valid @RequestBody ScriptUpdateRequest request) {
        log.info("更新剧本: {}", id);
        Script script = scriptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("剧本不存在: " + id));
        script.setContentYaml(request.getContentYaml());
        script = scriptRepository.save(script);
        return toScriptResponse(script);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScript(@PathVariable Long id) {
        log.info("删除剧本: {}", id);
        if (!scriptRepository.existsById(id)) {
            throw new ResourceNotFoundException("剧本不存在: " + id);
        }
        scriptRepository.deleteById(id);
    }

    // ==================== Validation & Fix Endpoints ====================

    @PostMapping("/{id}/validate")
    public ValidationResultResponse validateScript(@PathVariable Long id) {
        log.info("验证剧本 YAML: {}", id);
        Script script = scriptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("剧本不存在: " + id));
        return schemaValidatorService.validate(script.getContentYaml());
    }

    @PostMapping("/{id}/fix")
    public ScriptResponse fixScript(@PathVariable Long id) {
        log.info("自动修复剧本 YAML: {}", id);
        Script script = scriptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("剧本不存在: " + id));
        String fixedYaml = schemaValidatorService.autoFixCommonErrors(script.getContentYaml());
        script.setContentYaml(fixedYaml);
        script = scriptRepository.save(script);
        return toScriptResponse(script);
    }

    // ==================== Analysis Endpoint ====================

    @GetMapping("/project/{projectId}/analysis")
    public AnalysisResponse getAnalysis(@PathVariable Long projectId) {
        log.debug("获取项目 {} 的分析结果", projectId);
        Analysis analysis = analysisRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "项目分析结果不存在: " + projectId));
        return toAnalysisResponse(analysis);
    }

    // ==================== Helper Methods ====================

    private ScriptResponse toScriptResponse(Script script) {
        Map<String, Object> validationResult = Collections.emptyMap();
        try {
            if (script.getValidationResultJson() != null
                    && !script.getValidationResultJson().isBlank()) {
                validationResult = objectMapper.readValue(
                        script.getValidationResultJson(),
                        new TypeReference<Map<String, Object>>() {});
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

    private AnalysisResponse toAnalysisResponse(Analysis analysis) {
        List<Map<String, Object>> characters = parseJsonArray(analysis.getCharactersJson());
        List<Map<String, Object>> locations = parseJsonArray(analysis.getLocationsJson());
        List<Map<String, Object>> events = parseJsonArray(analysis.getEventsJson());
        List<Map<String, Object>> chapterSummaries = parseJsonArray(analysis.getChapterSummariesJson());

        return AnalysisResponse.builder()
                .id(analysis.getId())
                .projectId(analysis.getProject().getId())
                .characters(characters)
                .locations(locations)
                .events(events)
                .chapterSummaries(chapterSummaries)
                .createdAt(analysis.getCreatedAt())
                .build();
    }

    private List<Map<String, Object>> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("解析JSON数组失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
