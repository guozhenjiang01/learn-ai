package com.example.learnai.controller;

import com.example.learnai.tool.PdfToMarkdownService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * PDF 转换控制器
 * 提供 PDF 转 Markdown 的 REST 接口
 *
 * @author guozhenjiang9
 */
@Slf4j
@RestController
@RequestMapping("/pdf-convert")
public class PdfConvertController {

    @Autowired
    private PdfToMarkdownService pdfToMarkdownService;

    /**
     * 将 PDF 转换为 Markdown（返回内容）
     *
     * @param filePath PDF 文件路径
     * @return Markdown 文本内容
     */
    @GetMapping("/to-markdown")
    public Map<String, Object> toMarkdown(@RequestParam("filePath") String filePath) {
        Map<String, Object> result = new HashMap<>();
        try {
            String markdown = pdfToMarkdownService.convert(filePath);
            result.put("success", true);
            result.put("content", markdown);
        } catch (IOException e) {
            log.error("PDF 转换失败", e);
            result.put("success", false);
            result.put("message", "转换失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 将 PDF 转换为 Markdown 并保存为文件
     *
     * @param filePath   PDF 文件路径
     * @param outputPath 输出路径（可选，默认与PDF同目录同名.md）
     * @return 输出文件路径
     */
    @PostMapping("/save-markdown")
    public Map<String, Object> saveMarkdown(
            @RequestParam("filePath") String filePath,
            @RequestParam(value = "outputPath", required = false) String outputPath) {
        Map<String, Object> result = new HashMap<>();
        try {
            String savedPath = pdfToMarkdownService.convertAndSave(filePath, outputPath);
            result.put("success", true);
            result.put("outputPath", savedPath);
            result.put("message", "转换并保存成功");
        } catch (IOException e) {
            log.error("PDF 转换保存失败", e);
            result.put("success", false);
            result.put("message", "转换失败: " + e.getMessage());
        }
        return result;
    }
}