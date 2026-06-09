package com.example.learnai.rag.splitter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 递归/混合切分器
 * <p>
 * 最实用的思路：语义优先、长度兜底。
 * <p>
 * 不是一上来就按固定长度硬砍，而是先尝试保留大的语义单元：
 * 1. 先按章节（标题）切
 * 2. 章节太长 -> 按段落切
 * 3. 段落太长 -> 按句子切
 * 4. 句子还是太长 -> 退到按字符截断
 * <p>
 * 好处：大多数 chunk 既完整，又不会失控地过大。
 * 比如 API 文档中一个接口说明如果超过 size，会被拆成"参数说明""请求示例"
 * "响应示例""错误码"几个子块，而不是被硬切到语义断裂。
 * <p>
 * 策略：语义优先 + 长度兜底 = 检索精度和上下文完整度的最佳平衡。
 *
 * @author guozhenjiang9
 */
@Slf4j
@Component
public class RecursiveTextSplitter {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP_SIZE = 50;

    /**
     * 递归分隔符列表（从大语义单元到小语义单元）
     * 依次尝试：标题 -> 空行（段落） -> 换行 -> 句号 -> 字符
     */
    private static final List<String> DEFAULT_SEPARATORS = Arrays.asList(
            "\n#{1,6} ",    // Markdown 标题（正则模式）
            "\n\n",         // 段落（双换行）
            "\n",           // 换行
            "。",           // 中文句号
            ".",            // 英文句号
            " ",            // 空格
            ""              // 兜底：逐字符
    );

    /**
     * 使用默认参数进行递归切分
     *
     * @param text 原始文本
     * @return 切分后的 chunk 列表
     */
    public List<String> split(String text) {
        return split(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE);
    }

    /**
     * 递归切分文本
     *
     * @param text        原始文本
     * @param chunkSize   最大 chunk 大小
     * @param overlapSize 重叠大小
     * @return 切分后的 chunk 列表
     */
    public List<String> split(String text, int chunkSize, int overlapSize) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> result = recursiveSplit(text, DEFAULT_SEPARATORS, chunkSize, overlapSize);
        log.info("[递归切分] 完成，共 {} 个块（chunkSize={}, overlap={}）",
                result.size(), chunkSize, overlapSize);
        return result;
    }

    /**
     * 核心递归逻辑
     */
    private List<String> recursiveSplit(String text, List<String> separators, int chunkSize, int overlapSize) {
        List<String> finalChunks = new ArrayList<>();

        // 如果文本已经足够小，直接返回
        if (text.length() <= chunkSize) {
            String trimmed = text.trim();
            if (!trimmed.isEmpty()) {
                finalChunks.add(trimmed);
            }
            return finalChunks;
        }

        // 找到合适的分隔符
        String separator = "";
        List<String> remainingSeparators = separators;

        for (int i = 0; i < separators.size(); i++) {
            String sep = separators.get(i);
            if (sep.isEmpty()) {
                separator = sep;
                remainingSeparators = List.of();
                break;
            }
            // 第一个分隔符用正则匹配标题
            String[] parts;
            if (i == 0) {
                parts = text.split("(?=\\n#{1,6} )");
            } else {
                parts = text.split(java.util.regex.Pattern.quote(sep));
            }
            if (parts.length > 1) {
                separator = sep;
                remainingSeparators = separators.subList(i + 1, separators.size());
                break;
            }
        }

        // 用找到的分隔符切分
        String[] splits;
        if (separator.isEmpty()) {
            // 兜底：按字符切
            splits = splitByCharLimit(text, chunkSize);
        } else if (separators.indexOf(separator) == 0) {
            splits = text.split("(?=\\n#{1,6} )");
        } else {
            splits = text.split(java.util.regex.Pattern.quote(separator));
        }

        // 合并小块，递归处理大块
        StringBuilder currentChunk = new StringBuilder();
        for (String piece : splits) {
            if (currentChunk.length() + piece.length() <= chunkSize) {
                currentChunk.append(piece);
                if (!separator.isEmpty()) {
                    currentChunk.append(separator);
                }
            } else {
                // 保存当前积累的块
                if (!currentChunk.isEmpty()) {
                    finalChunks.add(currentChunk.toString().trim());
                    // overlap：保留末尾部分
                    String overlap = currentChunk.substring(
                            Math.max(0, currentChunk.length() - overlapSize));
                    currentChunk = new StringBuilder(overlap);
                }
                // 如果单个 piece 仍然超过 chunkSize，递归处理
                if (piece.length() > chunkSize && !remainingSeparators.isEmpty()) {
                    finalChunks.addAll(recursiveSplit(piece, remainingSeparators, chunkSize, overlapSize));
                } else {
                    currentChunk.append(piece);
                }
            }
        }
        // 最后剩余的块
        if (!currentChunk.isEmpty()) {
            String trimmed = currentChunk.toString().trim();
            if (!trimmed.isEmpty()) {
                finalChunks.add(trimmed);
            }
        }

        return finalChunks;
    }

    /**
     * 兜底方案：按字符数硬切
     */
    private String[] splitByCharLimit(String text, int chunkSize) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            parts.add(text.substring(i, Math.min(i + chunkSize, text.length())));
        }
        return parts.toArray(new String[0]);
    }
}