package com.example.learnai.tool;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 转 Markdown 工具服务
 * 解析 PDF 文本结构，根据字体大小识别标题层级，转换为 Markdown 格式
 *
 * @author guozhenjiang9
 */
@Slf4j
@Service
public class PdfToMarkdownService {

    /**
     * 将 PDF 文件转换为 Markdown 字符串
     *
     * @param pdfPath PDF 文件路径
     * @return Markdown 格式文本
     */
    public String convert(String pdfPath) throws IOException {
        Path path = Path.of(pdfPath);
        if (!Files.exists(path)) {
            throw new IOException("PDF 文件不存在: " + pdfPath);
        }

        try (InputStream is = Files.newInputStream(path);
             PDDocument document = Loader.loadPDF(is.readAllBytes())) {
            int totalPages = document.getNumberOfPages();
            log.info("开始转换 PDF: {}，共 {} 页", pdfPath, totalPages);

            StringBuilder markdown = new StringBuilder();

            // 先通过字体分析获取结构化内容
            List<TextBlock> blocks = extractTextBlocks(document);

            if (blocks.isEmpty()) {
                // 回退到纯文本提取
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                markdown.append(formatPlainText(text));
            } else {
                markdown.append(formatBlocks(blocks));
            }

            log.info("PDF 转换完成，输出 {} 字符", markdown.length());
            return markdown.toString();
        }
    }

    /**
     * 转换 PDF 并保存为 .md 文件
     *
     * @param pdfPath    PDF 文件路径
     * @param outputPath 输出 .md 文件路径（为空时自动生成）
     * @return 输出文件路径
     */
    public String convertAndSave(String pdfPath, String outputPath) throws IOException {
        String markdown = convert(pdfPath);

        if (outputPath == null || outputPath.isEmpty()) {
            outputPath = pdfPath.replaceAll("\\.[pP][dD][fF]$", ".md");
        }

        Files.writeString(Path.of(outputPath), markdown, StandardCharsets.UTF_8);
        log.info("Markdown 文件已保存: {}", outputPath);
        return outputPath;
    }

    /**
     * 提取带字体信息的文本块
     */
    private List<TextBlock> extractTextBlocks(PDDocument document) throws IOException {
        List<TextBlock> blocks = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            final int pageNum = pageIndex + 1;

            PDFTextStripper stripper = new PDFTextStripper() {
                private float currentFontSize = -1;
                private StringBuilder currentLine = new StringBuilder();

                @Override
                protected void processTextPosition(TextPosition text) {
                    float fontSize = text.getFontSizeInPt();
                    // 字体大小变化时，保存当前块
                    if (currentFontSize > 0 && Math.abs(fontSize - currentFontSize) > 1.0f
                            && !currentLine.toString().trim().isEmpty()) {
                        blocks.add(new TextBlock(currentLine.toString().trim(), currentFontSize, pageNum));
                        currentLine = new StringBuilder();
                    }
                    currentFontSize = fontSize;
                    currentLine.append(text.getUnicode());
                    super.processTextPosition(text);
                }

                @Override
                protected void writeLineSeparator() throws IOException {
                    if (!currentLine.toString().trim().isEmpty()) {
                        blocks.add(new TextBlock(currentLine.toString().trim(), currentFontSize, pageNum));
                        currentLine = new StringBuilder();
                    }
                    super.writeLineSeparator();
                }
            };

            stripper.setStartPage(pageNum);
            stripper.setEndPage(pageNum);
            stripper.writeText(document, new StringWriter());
        }

        return blocks;
    }

    /**
     * 将文本块格式化为 Markdown
     */
    private String formatBlocks(List<TextBlock> blocks) {
        if (blocks.isEmpty()) return "";

        // 计算字体大小的统计信息，确定标题阈值
        float maxFontSize = blocks.stream().map(b -> b.fontSize).max(Float::compare).orElse(12f);
        float avgFontSize = (float) blocks.stream().mapToDouble(b -> b.fontSize).average().orElse(12.0);

        StringBuilder sb = new StringBuilder();
        for (TextBlock block : blocks) {
            String text = block.text.trim();
            if (text.isEmpty()) continue;

            if (block.fontSize >= maxFontSize - 1 && block.fontSize > avgFontSize + 4) {
                sb.append("# ").append(text).append("\n\n");
            } else if (block.fontSize > avgFontSize + 2) {
                sb.append("## ").append(text).append("\n\n");
            } else if (block.fontSize > avgFontSize + 1) {
                sb.append("### ").append(text).append("\n\n");
            } else {
                sb.append(text).append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * 纯文本回退格式化
     */
    private String formatPlainText(String text) {
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                sb.append("\n");
            } else {
                sb.append(trimmed).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 文本块：包含文本内容、字体大小和页码
     */
    private record TextBlock(String text, float fontSize, int page) {
    }
}