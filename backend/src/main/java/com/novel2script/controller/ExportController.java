package com.novel2script.controller;

import com.novel2script.dto.ExportRequest;
import com.novel2script.entity.Script;
import com.novel2script.exception.GlobalExceptionHandler.BadRequestException;
import com.novel2script.exception.GlobalExceptionHandler.ResourceNotFoundException;
import com.novel2script.repository.ScriptRepository;
import com.novel2script.service.ExportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ScriptRepository scriptRepository;
    private final ExportService exportService;

    @PostMapping
    public ResponseEntity<byte[]> exportScript(@Valid @RequestBody ExportRequest request) {
        log.info("导出剧本，ID: {}, 格式: {}", request.getScriptId(), request.getFormat());

        Script script = scriptRepository.findById(request.getScriptId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "剧本不存在: " + request.getScriptId()));

        String yamlContent = script.getContentYaml();
        String format = request.getFormat();

        byte[] data;
        String contentType;
        String filename;

        if ("yaml".equals(format)) {
            String exported = exportService.exportYaml(yamlContent);
            data = exported.getBytes(StandardCharsets.UTF_8);
            contentType = "text/yaml; charset=UTF-8";
            filename = "script.yaml";
        } else if ("markdown".equals(format)) {
            String exported = exportService.exportMarkdown(yamlContent);
            data = exported.getBytes(StandardCharsets.UTF_8);
            contentType = "text/markdown; charset=UTF-8";
            filename = "script.md";
        } else if ("pdf".equals(format)) {
            data = exportService.exportPdf(yamlContent);
            contentType = MediaType.APPLICATION_PDF_VALUE;
            filename = "script.pdf";
        } else {
            throw new BadRequestException("不支持的导出格式: " + format);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(data.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(data);
    }
}
