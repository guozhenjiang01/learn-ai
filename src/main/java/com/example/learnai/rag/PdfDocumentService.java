package com.example.learnai.rag;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 文档读取与分块服务
 * 支持读取 PDF 文件并按照固定大小进行分块
 *
 * @author guozhenjiang9
 */
@Slf4j
@Service
public class PdfDocumentService {

    /**
     * 默认分块大小（字符数）
     */
    private static final int DEFAULT_CHUNK_SIZE = 500;

    /**
     * 默认分块重叠大小（字符数）
     */
    private static final int DEFAULT_OVERLAP_SIZE = 100;

    /**
     * 读取 PDF 文件内容
     *
     * @param filePath PDF 文件路径
     * @return 文件的完整文本内容
     */
    public String readPdf(String filePath) throws IOException {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new IOException("PDF 文件不存在: " + filePath);
        }

        try (InputStream is = Files.newInputStream(path);
             PDDocument document = Loader.loadPDF(is.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("成功读取 PDF 文件: {}，共 {} 页，{} 字符",
                    filePath, document.getNumberOfPages(), text.length());
            return text;
        }
    }

    /**
     * 读取 PDF 并切分为文本块
     *
     * @param filePath PDF 文件路径
     * @return 文本块列表
     */
    public List<String> readAndSplit(String filePath) throws IOException {
        return readAndSplit(filePath, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE);
    }

    /**
     * 读取 PDF 并按指定大小切分为文本块（带重叠）
     *
     * @param filePath    PDF 文件路径
     * @param chunkSize   每块大小
     * @param overlapSize 重叠大小
     * @return 文本块列表
     */
    public List<String> readAndSplit(String filePath, int chunkSize, int overlapSize) throws IOException {
        String content = readPdf(filePath);
        return splitText(content, chunkSize, overlapSize);
    }

    /**
     * 将文本按固定大小切分（带重叠）
     */
    private List<String> splitText(String text, int chunkSize, int overlapSize) {
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
        log.info("文本切分完成，共 {} 个块（chunkSize={}, overlap={}）", chunks.size(), chunkSize, overlapSize);
        return chunks;
    }
}