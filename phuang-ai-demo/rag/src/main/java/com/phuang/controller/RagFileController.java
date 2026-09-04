package com.phuang.controller;

import com.phuang.util.MinioService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 *
 * @description RagFileController
 * @author huangpeng
 * @since 2026/8/29
 */
@RestController
@RequestMapping("rag/file")
public class RagFileController {

    @Resource
    private MinioService minioService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file,
                                             @RequestParam("name") String name) throws Exception {
        try {
            minioService.uploadFile(file, name);
            return ResponseEntity.ok("上传成功: " + name);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/download-url")
    public ResponseEntity<String> getDownloadUrl(@RequestParam("fileName") String fileName) {
        try {
            String url = minioService.getPresignedUrl(fileName);
            return ResponseEntity.ok(url);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("生成下载链接失败");
        }
    }
}
