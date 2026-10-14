package com.sprouttrail.image.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sprouttrail.image.model.request.EditImageRequest;
import com.sprouttrail.image.model.request.GenerateImageRequest;
import com.sprouttrail.image.model.response.GeneratedImage;
import com.sprouttrail.image.model.response.ImageHistoryResponse;
import com.sprouttrail.image.service.ImageService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 对外提供文生图和基于原图的图片优化接口
 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

    @Resource
    private ImageService imageService;

    /**
     * 生成、保存并返回一张 PNG 图片
     *
     * 接口直接返回图片二进制，前端可以使用同一次响应完成预览和下载
     * {@code X-Image-Filename} 提供服务器生成的安全文件名
     *
     * @param request 已校验的文生图请求
     * @return PNG 图片响应
     */
    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generate(@Valid @RequestBody GenerateImageRequest request) {
        GeneratedImage generatedImage = imageService.generate(
                request.getPrompt(), request.effectivePrompt(), request.effectiveAspectRatio());
        return imageResponse(generatedImage);
    }

    /**
     * 接收单张 JPG/PNG 原图与编辑要求，生成并保存一张优化结果。
     *
     * <p>浏览器到本项目使用 multipart 上传；本项目到模型使用 JSON/Base64
     * 文件在请求结束后由框架清理，只有生成结果会持久保存。
     *
     * @param request 图片、原样保留的编辑要求以及可选画幅
     * @return PNG 图片响应
     */
    @PostMapping(value = "/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> edit(@Valid @ModelAttribute EditImageRequest request) {
        return imageResponse(imageService.edit(request.getImage(), request.getPrompt(),
                request.effectiveAspectRatio()));
    }

    /**
     * 查询当前登录用户的文生图历史记录
     * @param page
     * @param pageSize
     * @return
     */
    @GetMapping("/history")
    public ImageHistoryResponse history(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return imageService.getHistory(StpUtil.getLoginIdAsLong(), page, pageSize);
    }

    /**
     * 下载当前登录用户拥有的历史图片
     * @param recordId
     * @return
     */
    @GetMapping("/history/{recordId}/download")
    public ResponseEntity<byte[]> downloadHistoryImage(@PathVariable Long recordId) {
        GeneratedImage generatedImage = imageService.downloadHistoryImage(StpUtil.getLoginIdAsLong(), recordId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setCacheControl(CacheControl.noStore());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(generatedImage.getFilename())
                .build());
        return ResponseEntity.ok().headers(headers).body(generatedImage.getBytes());
    }

    /**
     * 两种生成方式共用相同的预览、下载及文件名响应结构
     * @param generatedImage
     * @return
     */
    private ResponseEntity<byte[]> imageResponse(GeneratedImage generatedImage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setCacheControl(CacheControl.noStore());
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(generatedImage.getFilename())
                .build());
        headers.add("X-Image-Filename", generatedImage.getFilename());

        return ResponseEntity.ok()
                .headers(headers)
                .body(generatedImage.getBytes());
    }
}
