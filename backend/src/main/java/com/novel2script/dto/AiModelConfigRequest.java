package com.novel2script.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiModelConfigRequest {
    @NotBlank(message = "请选择厂商")
    private String provider;

    @NotBlank(message = "请输入模型名称")
    private String modelName;

    @NotBlank(message = "请输入 API Key")
    private String apiKey;

    private String baseUrl; // optional, auto-fill based on provider
}
