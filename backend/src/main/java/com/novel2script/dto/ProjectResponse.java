package com.novel2script.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ProjectResponse {
    private Long id;
    private String title;
    private String author;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int chapterCount;
    private long totalWords;
    private List<ChapterResponse> chapters;
}
