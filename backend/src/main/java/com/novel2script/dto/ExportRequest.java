package com.novel2script.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ExportRequest {
    @NotNull(message = "剧本ID不能为空")
    private Long scriptId;

    @NotBlank(message = "导出格式不能为空")
    @Pattern(regexp = "^(yaml|markdown|pdf)$", message = "支持的格式: yaml, markdown, pdf")
    private String format;
}
