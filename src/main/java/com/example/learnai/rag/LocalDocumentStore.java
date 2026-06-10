package com.example.learnai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 本地文档存储：读取 md 文件，按标题(##)切分为段落，使用向量语义检索
 *
 * @author guozhenjiang9
 */
@Slf4j
@Component
public class LocalDocumentStore {

    private final List<String> chunks = new ArrayList<>();
    private final List<float[]> chunkEmbeddings = new ArrayList<>();

    @Autowired
    private ZhipuEmbeddingService zhipuEmbeddingService;

    /**
     * 按所有标题层级(#、##、###等)切分文档
     */
    private List<String> splitByHeading(String content) {
        List<String> result = new ArrayList<>();
        String[] lines = content.split("\n");
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            // 匹配所有标题层级：以一个或多个 # 开头，后跟空格
            if (line.matches("^#{1,6} .+") && !current.isEmpty()) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(line).append("\n");
        }
        if (!current.isEmpty()) {
            result.add(current.toString().trim());
        }
        return result;
    }

    /**
     * 基于向量语义相似度检索相关段落
     */
    public List<String> search(String query, int topK) {
        try {
            // 使用智谱计算 query 的向量
            List<float[]> queryEmbeddings = zhipuEmbeddingService.embed(List.of(query));
            float[] queryVec = queryEmbeddings.get(0);

            // 计算与每个段落的余弦相似度，排序取 topK
            List<int[]> scored = new ArrayList<>();
            for (int i = 0; i < chunkEmbeddings.size(); i++) {
                scored.add(new int[]{i});
            }

            scored.sort(Comparator.comparingDouble(a -> -cosineSimilarity(queryVec, chunkEmbeddings.get(a[0]))));

            List<String> results = new ArrayList<>();
            for (int i = 0; i < Math.min(topK, scored.size()); i++) {
                results.add(chunks.get(scored.get(i)[0]));
            }
            return results;
        } catch (IOException e) {
            log.error("搜索时生成向量失败", e);
            return List.of();
        }
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}