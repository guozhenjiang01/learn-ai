package com.example.learnai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Markdown 文档读取与分块服务
 * 支持多种切分方式：固定大小、按标题、按段落
 *
 * @author guozhenjiang9
 */
@Slf4j
@Service
public class MarkdownDocumentService {

    /**
     * 默认分块大小（字符数）
     */
    private static final int DEFAULT_CHUNK_SIZE = 500;

    /**
     * 默认分块重叠大小（字符数）
     */
    private static final int DEFAULT_OVERLAP_SIZE = 100;

    /**
     * 切分方式枚举
     */
    public enum SplitStrategy {
        /**
         * 固定大小切分（带重叠）
         */
        FIXED_SIZE,
        /**
         * 按标题切分（按 # 标题层级）
         */
        BY_HEADING,
        /**
         * 按段落切分（按空行分隔）
         */
        BY_PARAGRAPH
    }

    /**
     * 读取 Markdown 文件内容
     *
     * @param filePath Markdown 文件路径
     * @return 文件的完整文本内容
     */
    public String readMarkdown(String filePath) throws IOException {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new IOException("Markdown 文件不存在: " + filePath);
        }
        String content = Files.readString(path);
        log.info("成功读取 Markdown 文件: {}，共 {} 字符", filePath, content.length());
        return content;
    }

    /**
     * 读取 Markdown 并按指定策略切分
     *
     * @param filePath 文件路径
     * @param strategy 切分策略
     * @return 文本块列表
     */
    public List<String> readAndSplit(String filePath, SplitStrategy strategy) throws IOException {
        String content = readMarkdown(filePath);
        return switch (strategy) {
            case FIXED_SIZE -> splitByFixedSize(content, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE);
            case BY_HEADING -> splitByHeading(content);
            case BY_PARAGRAPH -> splitByParagraph(content);
        };
    }

    /**
     * 固定大小切分（带重叠）
     */
    private List<String> splitByFixedSize(String text, int chunkSize, int overlapSize) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            start += chunkSize - overlapSize;
        }
        log.info("固定大小切分完成，共 {} 个块（chunkSize={}, overlap={}）", chunks.size(), chunkSize, overlapSize);
        return chunks;
    }

    /**
     * 按标题切分（按 # 标题行分割）
     * 每个标题及其下方内容作为一个块
     */
    private List<String> splitByHeading(String text) {
        List<String> chunks = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder currentChunk = new StringBuilder();

        for (String line : lines) {
            // 遇到标题行且当前块非空，则保存当前块
            if (line.matches("^#{1,6}\\s+.*") && !currentChunk.isEmpty()) {
                String chunk = currentChunk.toString().trim();
                if (!chunk.isEmpty()) {
                    chunks.add(chunk);
                }
                currentChunk = new StringBuilder();
            }
            currentChunk.append(line).append("\n");
        }

        // 保存最后一个块
        if (!currentChunk.isEmpty()) {
            String chunk = currentChunk.toString().trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
        }

        log.info("按标题切分完成，共 {} 个块", chunks.size());
        return chunks;
    }

    /**
     * 按段落切分（按空行分隔）
     */
    private List<String> splitByParagraph(String text) {
        String[] paragraphs = text.split("\n\\s*\n");
        List<String> chunks = Arrays.stream(paragraphs)
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .collect(Collectors.toList());
        log.info("按段落切分完成，共 {} 个块", chunks.size());
        return chunks;
    }

    /**
     * 根据切分策略生成对应的 ES 索引名
     *
     * @param strategy 切分策略
     * @return ES 索引名
     */
    public String getIndexName(SplitStrategy strategy) {
        return switch (strategy) {
            case FIXED_SIZE -> "md_vectors_fixed_size";
            case BY_HEADING -> "md_vectors_by_heading";
            case BY_PARAGRAPH -> "md_vectors_by_paragraph";
        };
    }
}