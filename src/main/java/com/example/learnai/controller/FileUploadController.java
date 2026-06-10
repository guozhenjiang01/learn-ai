package com.example.learnai.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author guozhenjiang9
 */
@RestController
@RequestMapping("/file")
public class FileUploadController {

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();

        if (file.isEmpty()) {
            result.put("success", false);
            result.put("message", "文件不能为空");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String savedFilename = UUID.randomUUID().toString() + extension;

            Path targetPath = uploadDir.resolve(savedFilename);
            file.transferTo(targetPath.toFile());

            result.put("success", true);
            result.put("message", "上传成功");
            result.put("originalFilename", originalFilename);
            result.put("savedFilename", savedFilename);
            result.put("size", file.getSize());

            return ResponseEntity.ok(result);
        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "上传失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> listFiles() {
        try {
            Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
            if (!Files.exists(uploadDir)) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            List<Map<String, Object>> files = Files.list(uploadDir)
                .filter(Files::isRegularFile)
                .sorted((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .map(p -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", p.getFileName().toString());
                    try {
                        long size = Files.size(p);
                        String sizeStr;
                        if (size < 1024) sizeStr = size + "B";
                        else if (size < 1024 * 1024) sizeStr = String.format("%.1fKB", size / 1024.0);
                        else sizeStr = String.format("%.1fMB", size / 1024.0 / 1024.0);
                        item.put("size", sizeStr);
                    } catch (IOException e) {
                        item.put("size", "?");
                    }
                    return item;
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(files);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}