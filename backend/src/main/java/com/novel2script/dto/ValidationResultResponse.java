package com.novel2script.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ValidationResultResponse {
    private boolean valid;
    private List<Map<String, String>> errors;
    private List<Map<String, String>> warnings;
}
