package com.example.learnai.rag;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final String FILE_PATH = "/Users/guozhenjiang9/data/quick-start.md";

    private final List<String> chunks = new ArrayList<>();
    private final List<float[]> chunkEmbeddings = new ArrayList<>();
    private DashScopeEmbeddingModel embeddingModel;

    @PostConstruct
    public void init() {
        // 初始化 embedding 模型，复用 DashScope API
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey("sk-3566bb7ecf20404ba9fbbd16db1ca564")
                .build();
        embeddingModel = new DashScopeEmbeddingModel(dashScopeApi);

        try {
            String content = Files.readString(Path.of(FILE_PATH), StandardCharsets.UTF_8);
            List<String> splitChunks = splitByHeading(content);
            chunks.addAll(splitChunks);

            // 对所有段落计算向量
            List<float[]> embeddings = embeddingModel.embed(chunks);
            chunkEmbeddings.addAll(embeddings);

            log.info("文档加载完成，共切分为 {} 个段落，已生成向量", chunks.size());
        } catch (IOException e) {
            log.error("读取文档失败: {}", FILE_PATH, e);
        }
    }

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
        // 计算 query 的向量
        List<float[]> queryEmbeddings = embeddingModel.embed(List.of(query));
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