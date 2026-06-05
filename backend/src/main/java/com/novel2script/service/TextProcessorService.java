package com.novel2script.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class TextProcessorService {

    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "(?m)^\\s*(第[一二三四五六七八九十百千零\\d]+章[\\s  :：]*[^\\n]*" +
            "|Chapter\\s+\\d+[\\s  :：]*[^\\n]*" +
            "|第\\d+节[\\s  :：]*[^\\n]*" +
            "|第[一二三四五六七八九十百千零\\d]+回[\\s  :：]*[^\\n]*" +
            "|第[一二三四五六七八九十百千零\\d]+幕[\\s  :：]*[^\\n]*)"
    );

    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]{2,}");
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{3,}");

    /**
     * Clean text: normalize whitespace, remove invisible chars, unify punctuation.
     */
    public String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String cleaned = text;

        // Remove BOM and zero-width characters
        cleaned = cleaned.replace("\uFEFF", "");       // BOM
        cleaned = cleaned.replace("\u200B", "");       // Zero-width space
        cleaned = cleaned.replace("\u200C", "");       // Zero-width non-joiner
        cleaned = cleaned.replace("\u200D", "");       // Zero-width joiner
        cleaned = cleaned.replace("\u2060", "");       // Word joiner
        cleaned = cleaned.replace("\uFFFE", "");       // Reverse BOM

        // Unify punctuation: Chinese quotes to standard form
        cleaned = cleaned.replace("\u201C", "\"");     // Left double quote
        cleaned = cleaned.replace("\u201D", "\"");     // Right double quote
        cleaned = cleaned.replace("\u2018", "'");      // Left single quote
        cleaned = cleaned.replace("\u2019", "'");      // Right single quote
        cleaned = cleaned.replace("\u300C", "\"");     // CJK left corner bracket
        cleaned = cleaned.replace("\u300D", "\"");     // CJK right corner bracket
        cleaned = cleaned.replace("\u300E", "'");      // CJK left white corner bracket
        cleaned = cleaned.replace("\u300F", "'");      // CJK right white corner bracket

        // Unify dashes
        cleaned = cleaned.replace("\u2014", "--");     // Em dash
        cleaned = cleaned.replace("\u2013", "-");      // En dash
        cleaned = cleaned.replace("\u2015", "--");     // Horizontal bar

        // Normalize whitespace
        cleaned = MULTI_SPACE.matcher(cleaned).replaceAll(" ");
        cleaned = MULTI_NEWLINE.matcher(cleaned).replaceAll("\n\n");

        // Trim each line
        String[] lines = cleaned.split("\\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(lines[i].trim());
            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }

        return sb.toString().trim();
    }

    /**
     * Split text into chapters by detecting chapter boundary patterns.
     * Returns a list of maps with keys: chapterNumber, title, content.
     * If no chapter markers found, splits by roughly equal word count chunks.
     */
    public List<Map<String, Object>> splitChapters(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        String cleaned = cleanText(text);
        Matcher matcher = CHAPTER_PATTERN.matcher(cleaned);

        List<Integer> boundaries = new ArrayList<>();
        List<String> titles = new ArrayList<>();

        while (matcher.find()) {
            boundaries.add(matcher.start());
            titles.add(matcher.group().trim());
        }

        List<Map<String, Object>> chapters = new ArrayList<>();

        if (boundaries.isEmpty()) {
            log.debug("未检测到章节标记，按字数均匀分割");
            return splitByWordCount(cleaned);
        }

        for (int i = 0; i < boundaries.size(); i++) {
            int start = boundaries.get(i);
            int end = (i + 1 < boundaries.size()) ? boundaries.get(i + 1) : cleaned.length();

            String chapterContent = cleaned.substring(start, end).trim();
            // Remove the title line from the content
            String titleLine = titles.get(i);
            if (chapterContent.startsWith(titleLine)) {
                chapterContent = chapterContent.substring(titleLine.length()).trim();
            }

            Map<String, Object> chapter = new LinkedHashMap<>();
            chapter.put("chapterNumber", i + 1);
            chapter.put("title", titleLine);
            chapter.put("content", chapterContent);
            chapters.add(chapter);
        }

        log.debug("检测到 {} 个章节", chapters.size());
        return chapters;
    }

    /**
     * Count words: Chinese characters counted individually, English words by space splitting.
     */
    public int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        int count = 0;
        String trimmed = text.trim();

        // Count Chinese characters individually
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (isChineseChar(c)) {
                count++;
            }
        }

        // Extract non-Chinese segments and count English words
        StringBuilder nonChinese = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (!isChineseChar(c)) {
                nonChinese.append(c);
            } else {
                nonChinese.append(' ');
            }
        }

        String[] words = nonChinese.toString().trim().split("\\s+");
        for (String word : words) {
            String w = word.trim();
            if (!w.isEmpty() && w.matches(".*[a-zA-Z0-9].*")) {
                count++;
            }
        }

        return count;
    }

    /**
     * Preprocess a single chapter text: clean and normalize.
     */
    public String preprocessChapter(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return cleanText(text);
    }

    // ---- Private helpers ----

    private boolean isChineseChar(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT;
    }

    private List<Map<String, Object>> splitByWordCount(String text) {
        List<Map<String, Object>> chapters = new ArrayList<>();
        int totalWords = countWords(text);
        int targetWordsPerChapter = Math.max(3000, totalWords / 10);
        int targetChapterCount = Math.max(1, totalWords / targetWordsPerChapter);

        // Split by paragraphs first
        String[] paragraphs = text.split("\\n\\n+");
        if (paragraphs.length == 0) {
            paragraphs = new String[]{text};
        }

        int chapterNumber = 1;
        StringBuilder currentChapter = new StringBuilder();
        int currentWordCount = 0;
        int wordsPerChunk = totalWords / Math.max(1, targetChapterCount);

        for (String paragraph : paragraphs) {
            currentChapter.append(paragraph).append("\n\n");
            currentWordCount += countWords(paragraph);

            if (currentWordCount >= wordsPerChunk && chapterNumber < targetChapterCount) {
                Map<String, Object> chapter = new LinkedHashMap<>();
                chapter.put("chapterNumber", chapterNumber);
                chapter.put("title", "第" + chapterNumber + "章");
                chapter.put("content", currentChapter.toString().trim());
                chapters.add(chapter);

                chapterNumber++;
                currentChapter = new StringBuilder();
                currentWordCount = 0;
            }
        }

        // Add remaining text as last chapter
        if (!currentChapter.toString().isBlank()) {
            Map<String, Object> chapter = new LinkedHashMap<>();
            chapter.put("chapterNumber", chapterNumber);
            chapter.put("title", "第" + chapterNumber + "章");
            chapter.put("content", currentChapter.toString().trim());
            chapters.add(chapter);
        }

        log.debug("按字数均匀分割为 {} 个章节", chapters.size());
        return chapters;
    }
}
