package com.novel2script.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChapterRequest {
    private String title = "";
    @NotBlank(message = "章节内容不能为空")
    private String content;
}
