package com.example.learnai.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.*;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * Elasticsearch 向量存储服务
 * 使用 ES 的 dense_vector 字段实现向量存储和 KNN 检索
 *
 * @author guozhenjiang9
 */
@Slf4j
@Service
public class EsVectorStoreService {

    private static final String DEFAULT_INDEX = "pdf_vectors";
    private static final int VECTOR_DIMS = 2048;

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private ZhipuEmbeddingService zhipuEmbeddingService;

    /**
     * 创建 ES 索引（带 dense_vector 字段用于向量检索）
     */
    public void createIndexIfNotExists(String indexName) throws IOException {
        boolean exists = esClient.indices().exists(e -> e.index(indexName)).value();
        if (exists) {
            log.info("索引 {} 已存在，跳过创建", indexName);
            return;
        }

        esClient.indices().create(CreateIndexRequest.of(c -> c
                .index(indexName)
                .mappings(m -> m
                        .properties("content", Property.of(p -> p.text(TextProperty.of(t -> t.analyzer("ik_max_word")))))
                        .properties("embedding", Property.of(p -> p.denseVector(DenseVectorProperty.of(d -> d
                                .dims(VECTOR_DIMS)
                                .index(true)
                                .similarity("cosine")
                        ))))
                        .properties("source", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))))
                        .properties("chunk_index", Property.of(p -> p.integer(IntegerNumberProperty.of(i -> i))))
                )
        ));
        log.info("成功创建索引: {}", indexName);
    }

    /**
     * 将文本块列表写入 ES（自动生成向量）
     *
     * @param chunks         文本块列表（含 overlap 上下文，存储到 ES 中）
     * @param embeddingTexts 用于生成 embedding 的文本（不含 overlap）
     * @param source         来源标识（如文件名）
     * @param indexName      索引名
     */
    public void storeDocuments(List<String> chunks, List<String> embeddingTexts, String source, String indexName) throws IOException {
        createIndexIfNotExists(indexName);

        // 批量生成向量（使用不含 overlap 的文本）
        int batchSize = 25;
        List<float[]> allEmbeddings = new ArrayList<>();

        for (int i = 0; i < embeddingTexts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, embeddingTexts.size());
            List<String> batch = embeddingTexts.subList(i, end);
            List<float[]> batchEmbeddings = zhipuEmbeddingService.embed(batch);
            allEmbeddings.addAll(batchEmbeddings);
            log.info("已生成向量: {}/{}", allEmbeddings.size(), embeddingTexts.size());
        }

        // 批量写入 ES（存储含 overlap 的完整文本）
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        for (int i = 0; i < chunks.size(); i++) {
            final int idx = i;
            final float[] embedding = allEmbeddings.get(i);

            Map<String, Object> doc = new HashMap<>();
            doc.put("content", chunks.get(idx));
            doc.put("embedding", toDoubleList(embedding));
            doc.put("source", source);
            doc.put("chunk_index", idx);

            bulkBuilder.operations(op -> op
                    .index(index -> index
                            .index(indexName)
                            .id(source + "_" + idx)
                            .document(doc)
                    )
            );
        }

        BulkResponse bulkResponse = esClient.bulk(bulkBuilder.build());
        if (bulkResponse.errors()) {
            for (BulkResponseItem item : bulkResponse.items()) {
                if (item.error() != null) {
                    log.error("写入ES失败: {}", item.error().reason());
                }
            }
            throw new IOException("部分文档写入 ES 失败");
        }

        log.info("成功将 {} 个文本块写入索引 {}", chunks.size(), indexName);
    }

    /**
     * 将文本块列表写入 ES（embedding 和存储使用相同文本）
     */
    public void storeDocuments(List<String> chunks, String source, String indexName) throws IOException {
        storeDocuments(chunks, chunks, source, indexName);
    }

    /**
     * 将文本块列表写入默认索引
     */
    public void storeDocuments(List<String> chunks, String source) throws IOException {
        storeDocuments(chunks, chunks, source, DEFAULT_INDEX);
    }

    /**
     * 基于向量相似度搜索（KNN）
     *
     * @param query 查询文本
     * @param topK  返回结果数量
     * @return 相似度最高的文本块列表
     */
    public List<String> search(String query, int topK) throws IOException {
        return search(query, topK, DEFAULT_INDEX);
    }

    /**
     * 基于向量相似度搜索（KNN）
     *
     * @param query     查询文本
     * @param topK      返回结果数量
     * @param indexName 索引名
     * @return 相似度最高的文本块列表
     */
    public List<String> search(String query, int topK, String indexName) throws IOException {
        // 生成查询向量
        List<float[]> queryEmbeddings = zhipuEmbeddingService.embed(List.of(query));
        float[] queryVector = queryEmbeddings.get(0);

        // 使用 KNN 向量搜索
        SearchResponse<Map> response = esClient.search(s -> s
                        .index(indexName)
                        .knn(knn -> knn
                                .field("embedding")
                                .queryVector(toDoubleList(queryVector))
                                .k((long) topK)
                                .numCandidates((long) topK * 10)
                        ),
                Map.class
        );

        List<String> results = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            if (hit.source() != null) {
                Object content = hit.source().get("content");
                if (content != null) {
                    results.add(content.toString());
                }
            }
        }

        log.info("向量搜索完成，查询: '{}'，返回 {} 个结果", query, results.size());
        return results;
    }

    /**
     * 删除索引
     */
    public void deleteIndex(String indexName) throws IOException {
        boolean exists = esClient.indices().exists(e -> e.index(indexName)).value();
        if (exists) {
            esClient.indices().delete(d -> d.index(indexName));
            log.info("成功删除索引: {}", indexName);
        }
    }

    /**
     * float[] 转 List<Float>（ES Client 需要）
     */
    private List<Float> toDoubleList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) {
            list.add(v);
        }
        return list;
    }
}