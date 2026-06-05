package com.novel2script.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChapterResponse {
    private Long id;
    private Long projectId;
    private int chapterNumber;
    private String title;
    private String content;
    private int wordCount;
    private LocalDateTime createdAt;
}
