package com.novel2script.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ScriptResponse {
    private Long id;
    private Long projectId;
    private int version;
    private String title;
    private String contentYaml;
    private Map<String, Object> validationResult;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
