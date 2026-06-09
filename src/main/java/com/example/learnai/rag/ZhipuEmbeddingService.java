package com.example.learnai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 智谱 Embedding 服务
 * 使用智谱 AI 的 embedding-3 模型生成文本向量
 *
 * @author guozhenjiang9
 */
@Slf4j
@Service
public class ZhipuEmbeddingService {

    private static final String API_URL = "https://open.bigmodel.cn/api/paas/v4/embeddings";
    private static final String API_KEY = "0cc30ac821a14c2f89aaa0e8944f0df3.Pj4vtMViY085cLrf";
    private static final String MODEL = "embedding-3";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 批量生成文本向量
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    public List<float[]> embed(List<String> texts) throws IOException {
        List<float[]> allEmbeddings = new ArrayList<>();

        // 智谱 embedding API 每次最多支持一条文本，逐条调用
        for (String text : texts) {
            float[] embedding = embedSingle(text);
            allEmbeddings.add(embedding);
        }

        log.info("智谱 embedding 完成，共生成 {} 个向量", allEmbeddings.size());
        return allEmbeddings;
    }

    /**
     * 单条文本生成向量
     */
    private float[] embedSingle(String text) throws IOException {
        Map<String, Object> requestBody = Map.of(
                "model", MODEL,
                "input", text
        );

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("智谱 embedding 调用失败，状态码: " + response.statusCode() + "，响应: " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode dataArray = root.get("data");
            if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) {
                throw new IOException("智谱 embedding 响应格式异常: " + response.body());
            }

            JsonNode embeddingNode = dataArray.get(0).get("embedding");
            float[] embedding = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }

            return embedding;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("智谱 embedding 调用被中断", e);
        }
    }

    /**
     * 获取向量维度（embedding-3 为 2048 维）
     */
    public int getDimensions() {
        return 2048;
    }
}