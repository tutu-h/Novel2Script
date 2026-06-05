package com.novel2script.controller;

import com.novel2script.dto.AiModelConfigRequest;
import com.novel2script.dto.AiModelConfigResponse;
import com.novel2script.service.AiModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/ai-models")
@RequiredArgsConstructor
public class AiModelController {

    private final AiModelService aiModelService;

    @GetMapping
    public List<AiModelConfigResponse> getAllModels() {
        return aiModelService.getAllModels();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AiModelConfigResponse createModel(@Valid @RequestBody AiModelConfigRequest request) {
        return aiModelService.createModel(request);
    }

    @PutMapping("/{id}")
    public AiModelConfigResponse updateModel(@PathVariable Long id,
                                              @Valid @RequestBody AiModelConfigRequest request) {
        return aiModelService.updateModel(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteModel(@PathVariable Long id) {
        aiModelService.deleteModel(id);
    }

    @PostMapping("/{id}/toggle")
    public AiModelConfigResponse toggleEnabled(@PathVariable Long id) {
        return aiModelService.toggleEnabled(id);
    }

    @PostMapping("/{id}/activate")
    public AiModelConfigResponse setActive(@PathVariable Long id) {
        return aiModelService.setActive(id);
    }

    @PostMapping("/{id}/test")
    public AiModelConfigResponse testModel(@PathVariable Long id) {
        return aiModelService.testModel(id);
    }
}
