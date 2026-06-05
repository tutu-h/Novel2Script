package com.novel2script.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectRequest {
    @NotBlank(message = "项目标题不能为空")
    @Size(max = 255)
    private String title;
    private String author = "";
    private String description = "";
}
