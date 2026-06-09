package com.example.learnai.rag.splitter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义边界切分器
 * <p>
 * 核心思想：尽量让每个 chunk 成为一个完整的信息单元。
 * 按文档天然结构来切：标题、段落、问答对、代码块等。
 * <p>
 * 优势：召回回来的内容更"像人话"，不是被腰斩的文本，而是能独立表达意思的内容。
 * 检索更稳，生成也更稳。
 * <p>
 * 适用场景：
 * - 普通说明文档：按标题和段落切
 * - Markdown 文档：按标题层级切
 * - FAQ 文档：按"一问一答"切
 * - 代码文档：保证完整代码块不被拆开
 * <p>
 * 代价：实现比固定长度复杂，效果依赖文档解析质量。
 *
 * @author guozhenjiang9
 */
@Slf4j
@Component
public class SemanticBoundarySplitter {

    /** Markdown 标题匹配：# ~ ###### */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+.+", Pattern.MULTILINE);

    /** 段落分隔（连续空行） */
    private static final Pattern PARAGRAPH_SEPARATOR = Pattern.compile("\\n\\s*\\n");

    /** FAQ 问答对匹配：以"问："/"Q："开头 */
    private static final Pattern QA_PATTERN = Pattern.compile("^(问[：:]|Q[：:]|\\d+[.、]\\s*问)", Pattern.MULTILINE);

    /**
     * 按 Markdown 标题层级切分
     * 每个标题及其下方内容作为一个独立 chunk
     *
     * @param text Markdown 文本
     * @return 按标题切分的 chunk 列表
     */
    public List<String> splitByHeading(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            // 遇到标题行，将之前积累的内容保存为一个 chunk
            if (line.matches("^#{1,6}\\s+.+") && !current.isEmpty()) {
                String chunk = current.toString().trim();
                if (!chunk.isEmpty()) {
                    chunks.add(chunk);
                }
                current = new StringBuilder();
            }
            current.append(line).append("\n");
        }
        // 最后一段
        if (!current.isEmpty()) {
            String chunk = current.toString().trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
        }

        log.info("[语义切分-标题] 完成，共 {} 个块", chunks.size());
        return chunks;
    }

    /**
     * 按段落切分
     * 以连续空行作为段落分隔符
     *
     * @param text 原始文本
     * @return 段落列表
     */
    public List<String> splitByParagraph(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        String[] paragraphs = PARAGRAPH_SEPARATOR.split(text);
        List<String> chunks = new ArrayList<>();
        for (String p : paragraphs) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                chunks.add(trimmed);
            }
        }

        log.info("[语义切分-段落] 完成，共 {} 个块", chunks.size());
        return chunks;
    }

    /**
     * 按问答对切分（适用于 FAQ 文档）
     * 每个"问：...答：..."作为一个独立 chunk
     *
     * @param text FAQ 格式文本
     * @return 问答对列表
     */
    public List<String> splitByQA(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        Matcher matcher = QA_PATTERN.matcher(text);
        List<Integer> positions = new ArrayList<>();

        while (matcher.find()) {
            positions.add(matcher.start());
        }

        if (positions.isEmpty()) {
            // 没有匹配到问答格式，回退到段落切分
            return splitByParagraph(text);
        }

        for (int i = 0; i < positions.size(); i++) {
            int start = positions.get(i);
            int end = (i + 1 < positions.size()) ? positions.get(i + 1) : text.length();
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
        }

        // 如果第一个问答前还有前言内容
        if (positions.get(0) > 0) {
            String preamble = text.substring(0, positions.get(0)).trim();
            if (!preamble.isEmpty()) {
                chunks.add(0, preamble);
            }
        }

        log.info("[语义切分-问答] 完成，共 {} 个块", chunks.size());
        return chunks;
    }

    /**
     * 按句子切分
     * 以句号、问号、感叹号作为分隔
     *
     * @param text 原始文本
     * @return 句子列表
     */
    public List<String> splitBySentence(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        // 中英文句号、问号、叹号
        String[] sentences = text.split("(?<=[。！？.!?])\\s*");
        List<String> chunks = new ArrayList<>();
        for (String s : sentences) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                chunks.add(trimmed);
            }
        }

        log.info("[语义切分-句子] 完成，共 {} 个块", chunks.size());
        return chunks;
    }
}