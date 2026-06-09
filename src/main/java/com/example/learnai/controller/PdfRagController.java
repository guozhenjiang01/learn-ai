package com.example.learnai.controller;

import com.example.learnai.agent.ChatModelFactory;
import com.example.learnai.rag.EsVectorStoreService;
import com.example.learnai.rag.MarkdownDocumentService;
import com.example.learnai.rag.MarkdownDocumentService.SplitStrategy;
import com.example.learnai.rag.PdfDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PDF 向量检索 RAG 控制器
 * 提供 PDF 上传入库和向量检索问答功能
 *
 * @author guozhenjiang9
 */
@Slf4j
@RestController
@RequestMapping("/pdf-rag")
public class PdfRagController {

    @Autowired
    private PdfDocumentService pdfDocumentService;

    @Autowired
    private MarkdownDocumentService markdownDocumentService;

    @Autowired
    private EsVectorStoreService esVectorStoreService;

    @Autowired
    private ChatModelFactory chatModelFactory;

    /**
     * 将 Markdown 文件读取并存入 ES 向量库
     * 不同切分方式会写入不同的 ES 索引
     *
     * @param filePath      Markdown 文件路径
     * @param splitStrategy 切分方式：FIXED_SIZE（固定大小）、BY_HEADING（按标题）、BY_PARAGRAPH（按段落）
     * @return 处理结果
     */
    @PostMapping("/ingest")
    public Map<String, Object> ingestMarkdown(
            @RequestParam("filePath") String filePath,
            @RequestParam(value = "splitStrategy", defaultValue = "FIXED_SIZE") SplitStrategy splitStrategy) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. 读取 Markdown 并按指定策略切分
            List<String> chunks = markdownDocumentService.readAndSplit(filePath, splitStrategy);

            // 2. 提取文件名作为 source 标识
            String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);

            // 3. 根据切分策略确定写入的 ES 索引
            String indexName = markdownDocumentService.getIndexName(splitStrategy);

            // 4. 存入 ES
            esVectorStoreService.storeDocuments(chunks, fileName, indexName);

            result.put("success", true);
            result.put("message", "Markdown 已成功入库");
            result.put("fileName", fileName);
            result.put("chunkCount", chunks.size());
            result.put("splitStrategy", splitStrategy.name());
            result.put("indexName", indexName);
        } catch (IOException e) {
            log.error("Markdown 入库失败", e);
            result.put("success", false);
            result.put("message", "Markdown 入库失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 基于 ES 向量检索进行 RAG 问答
     *
     * @param query 用户问题
     * @param topK  检索结果数量（默认3）
     * @return AI 回答
     */
    @GetMapping("/chat")
    public Map<String, Object> chat(
            @RequestParam("query") String query,
            @RequestParam(value = "topK", defaultValue = "3") int topK) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. 从 ES 向量库检索相关段落
            List<String> relevantDocs = esVectorStoreService.search(query, topK);
            String context = relevantDocs.stream().collect(Collectors.joining("\n\n---\n\n"));

            // 2. 构造 RAG prompt
            String prompt = """
                    你是一个智能文档助手，请根据以下参考文档内容回答用户的问题。
                    如果文档中没有相关信息，请如实告知。
                    
                    【参考文档】
                    %s
                    
                    【用户问题】
                    %s
                    """.formatted(context, query);

            // 3. 调用 LLM 生成回答
            String answer = chatModelFactory.getChatClient("deepSeekV4ProChatClient")
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();

            result.put("success", true);
            result.put("answer", answer);
            result.put("references", relevantDocs);
        } catch (IOException e) {
            log.error("RAG 查询失败", e);
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 纯向量搜索（不经过 LLM）
     */
    @GetMapping("/search")
    public Map<String, Object> search(
            @RequestParam("query") String query,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<String> docs = esVectorStoreService.search(query, topK);
            result.put("success", true);
            result.put("results", docs);
            result.put("count", docs.size());
        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "搜索失败: " + e.getMessage());
        }
        return result;
    }
}