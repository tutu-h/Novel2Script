package com.novel2script.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiModelConfigResponse {
    private Long id;
    private String provider;
    private String modelName;
    private String baseUrl;
    private Boolean enabled;
    private Boolean active;
    private String lastTestStatus;
    private LocalDateTime lastTestAt;
    private LocalDateTime createdAt;
    // Do NOT expose apiKey in response, only show masked version
    private String apiKeyMasked;
}
