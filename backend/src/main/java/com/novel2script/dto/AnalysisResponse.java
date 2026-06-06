package com.novel2script.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class AnalysisResponse {
    private Long id;
    private Long projectId;
    private List<Map<String, Object>> characters;
    private List<Map<String, Object>> locations;
    private List<Map<String, Object>> events;
    private List<Map<String, Object>> chapterSummaries;
    private List<Map<String, Object>> perChapterAnalysis;
    private List<Integer> analyzedChapters;
    private LocalDateTime createdAt;
}
