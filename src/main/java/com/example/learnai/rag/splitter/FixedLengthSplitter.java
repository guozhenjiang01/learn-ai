package com.example.learnai.rag.splitter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定长度切分器
 * <p>
 * 最简单的切分方式：按字符数硬切，前后加 overlap 缓冲带。
 * <p>
 * 优点：简单、稳定、工程成本低，适合快速跑通系统做 baseline。
 * 缺点：不理解内容，可能把一个完整语义单元从中间切断。
 * 比如"适用条件"和"退款时限"被切到两个 chunk 中，导致召回碎片化。
 *
 * @author guozhenjiang9
 */
@Slf4j
@Component
public class FixedLengthSplitter {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP_SIZE = 50;

    /**
     * 按固定长度切分文本（使用默认参数）
     *
     * @param text 原始文本
     * @return 切分后的文本块列表
     */
    public List<String> split(String text) {
        return split(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE);
    }

    /**
     * 按固定长度切分文本（带重叠）
     * <p>
     * overlap 的意义：让相邻 chunk 有重复区域，作为边界缓冲带，
     * 让卡在边缘的关键信息有更大概率完整落在某一个 chunk 里。
     * 一般经验：overlap 取 chunk size 的 10%~20% 是比较稳的起点。
     *
     * @param text        原始文本
     * @param chunkSize   每块大小（字符数）
     * @param overlapSize 重叠大小（字符数）
     * @return 切分后的文本块列表
     */
    public List<String> split(String text, int chunkSize, int overlapSize) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        if (overlapSize >= chunkSize) {
            throw new IllegalArgumentException("overlap 不能大于等于 chunkSize");
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            // 步进 = chunkSize - overlap，保证相邻块有 overlap 字符的重叠
            start += chunkSize - overlapSize;
        }

        log.info("[固定长度切分] 完成，共 {} 个块（chunkSize={}, overlap={}）",
                chunks.size(), chunkSize, overlapSize);
        return chunks;
    }
}