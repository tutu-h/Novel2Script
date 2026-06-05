package com.novel2script.controller;

import com.novel2script.dto.*;
import com.novel2script.entity.Chapter;
import com.novel2script.entity.Project;
import com.novel2script.exception.GlobalExceptionHandler.BadRequestException;
import com.novel2script.exception.GlobalExceptionHandler.ResourceNotFoundException;
import com.novel2script.repository.ChapterRepository;
import com.novel2script.repository.ProjectRepository;
import com.novel2script.service.TextProcessorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final ChapterRepository chapterRepository;
    private final TextProcessorService textProcessorService;

    // ==================== Project Endpoints ====================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createProject(@Valid @RequestBody ProjectRequest request) {
        log.info("创建项目: {}", request.getTitle());
        Project project = Project.builder()
                .title(request.getTitle())
                .author(request.getAuthor())
                .description(request.getDescription())
                .build();
        project = projectRepository.save(project);
        return toProjectResponse(project);
    }

    @GetMapping
    public List<ProjectResponse> listProjects() {
        log.debug("获取所有项目列表");
        return projectRepository.findAll().stream()
                .map(this::toProjectResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ProjectResponse getProject(@PathVariable Long id) {
        log.debug("获取项目: {}", id);
        Project project = findProjectOrThrow(id);
        return toProjectResponse(project);
    }

    @PutMapping("/{id}")
    public ProjectResponse updateProject(@PathVariable Long id,
                                          @Valid @RequestBody ProjectRequest request) {
        log.info("更新项目: {}", id);
        Project project = findProjectOrThrow(id);
        project.setTitle(request.getTitle());
        project.setAuthor(request.getAuthor());
        project.setDescription(request.getDescription());
        project = projectRepository.save(project);
        return toProjectResponse(project);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(@PathVariable Long id) {
        log.info("删除项目: {}", id);
        if (!projectRepository.existsById(id)) {
            throw new ResourceNotFoundException("项目不存在: " + id);
        }
        projectRepository.deleteById(id);
    }

    // ==================== Chapter Endpoints ====================

    @PostMapping("/{id}/chapters")
    @ResponseStatus(HttpStatus.CREATED)
    public ChapterResponse addChapter(@PathVariable Long id,
                                       @Valid @RequestBody ChapterRequest request) {
        log.info("为项目 {} 添加章节", id);
        Project project = findProjectOrThrow(id);

        long currentCount = chapterRepository.countByProjectId(id);
        int nextChapterNumber = (int) (currentCount + 1);

        int wordCount = textProcessorService.countWords(request.getContent());

        Chapter chapter = Chapter.builder()
                .project(project)
                .chapterNumber(nextChapterNumber)
                .title(request.getTitle())
                .content(request.getContent())
                .wordCount(wordCount)
                .build();
        chapter = chapterRepository.save(chapter);

        return toChapterResponse(chapter);
    }

    @GetMapping("/{id}/chapters")
    public List<ChapterResponse> listChapters(@PathVariable Long id) {
        log.debug("获取项目 {} 的章节列表", id);
        findProjectOrThrow(id);
        return chapterRepository.findByProjectIdOrderByChapterNumberAsc(id).stream()
                .map(this::toChapterResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}/chapters/{chapterNumber}")
    public ChapterResponse getChapter(@PathVariable Long id,
                                       @PathVariable Integer chapterNumber) {
        log.debug("获取项目 {} 的章节 {}", id, chapterNumber);
        findProjectOrThrow(id);
        Chapter chapter = findChapterOrThrow(id, chapterNumber);
        return toChapterResponse(chapter);
    }

    @PutMapping("/{id}/chapters/{chapterNumber}")
    public ChapterResponse updateChapter(@PathVariable Long id,
                                          @PathVariable Integer chapterNumber,
                                          @Valid @RequestBody ChapterRequest request) {
        log.info("更新项目 {} 的章节 {}", id, chapterNumber);
        findProjectOrThrow(id);
        Chapter chapter = findChapterOrThrow(id, chapterNumber);

        chapter.setTitle(request.getTitle());
        chapter.setContent(request.getContent());
        chapter.setWordCount(textProcessorService.countWords(request.getContent()));
        chapter = chapterRepository.save(chapter);

        return toChapterResponse(chapter);
    }

    @DeleteMapping("/{id}/chapters/{chapterNumber}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChapter(@PathVariable Long id,
                               @PathVariable Integer chapterNumber) {
        log.info("删除项目 {} 的章节 {}", id, chapterNumber);
        findProjectOrThrow(id);
        Chapter chapter = findChapterOrThrow(id, chapterNumber);
        chapterRepository.delete(chapter);

        // Re-number remaining chapters sequentially
        List<Chapter> remaining = chapterRepository.findByProjectIdOrderByChapterNumberAsc(id);
        for (int i = 0; i < remaining.size(); i++) {
            Chapter ch = remaining.get(i);
            if (ch.getChapterNumber() != i + 1) {
                ch.setChapterNumber(i + 1);
                chapterRepository.save(ch);
            }
        }
    }

    @PostMapping("/{id}/chapters/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ChapterResponse> batchAddChapters(@PathVariable Long id,
                                                    @RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.isBlank()) {
            throw new BadRequestException("文本内容不能为空");
        }

        log.info("批量添加章节到项目 {}", id);
        Project project = findProjectOrThrow(id);

        List<Map<String, Object>> splitResult = textProcessorService.splitChapters(text);
        if (splitResult.isEmpty()) {
            throw new BadRequestException("未能从文本中分割出章节");
        }

        long currentCount = chapterRepository.countByProjectId(id);
        int baseNumber = (int) currentCount;

        List<ChapterResponse> responses = new ArrayList<>();
        for (int i = 0; i < splitResult.size(); i++) {
            Map<String, Object> chapterData = splitResult.get(i);
            String title = (String) chapterData.getOrDefault("title", "");
            String content = (String) chapterData.getOrDefault("content", "");

            int wordCount = textProcessorService.countWords(content);

            Chapter chapter = Chapter.builder()
                    .project(project)
                    .chapterNumber(baseNumber + i + 1)
                    .title(title)
                    .content(content)
                    .wordCount(wordCount)
                    .build();
            chapter = chapterRepository.save(chapter);
            responses.add(toChapterResponse(chapter));
        }

        log.info("批量添加了 {} 个章节到项目 {}", responses.size(), id);
        return responses;
    }

    // ==================== Helper Methods ====================

    private Project findProjectOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("项目不存在: " + id));
    }

    private Chapter findChapterOrThrow(Long projectId, Integer chapterNumber) {
        return chapterRepository.findByProjectIdAndChapterNumber(projectId, chapterNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "章节不存在: 项目 " + projectId + ", 章节号 " + chapterNumber));
    }

    private ProjectResponse toProjectResponse(Project project) {
        List<Chapter> chapters = project.getChapters();
        int chapterCount = chapters.size();
        long totalWords = chapters.stream()
                .mapToLong(ch -> ch.getWordCount() != null ? ch.getWordCount() : 0)
                .sum();

        List<ChapterResponse> chapterResponses = chapters.stream()
                .map(this::toChapterResponse)
                .collect(Collectors.toList());

        return ProjectResponse.builder()
                .id(project.getId())
                .title(project.getTitle())
                .author(project.getAuthor())
                .description(project.getDescription())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .chapterCount(chapterCount)
                .totalWords(totalWords)
                .chapters(chapterResponses)
                .build();
    }

    private ChapterResponse toChapterResponse(Chapter chapter) {
        return ChapterResponse.builder()
                .id(chapter.getId())
                .projectId(chapter.getProject().getId())
                .chapterNumber(chapter.getChapterNumber())
                .title(chapter.getTitle())
                .content(chapter.getContent())
                .wordCount(chapter.getWordCount() != null ? chapter.getWordCount() : 0)
                .createdAt(chapter.getCreatedAt())
                .build();
    }
}
