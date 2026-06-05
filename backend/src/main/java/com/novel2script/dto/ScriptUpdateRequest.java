package com.novel2script.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ScriptUpdateRequest {
    @NotBlank(message = "YAML 内容不能为空")
    private String contentYaml;
}
