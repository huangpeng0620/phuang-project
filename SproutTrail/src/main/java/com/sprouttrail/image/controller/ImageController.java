package com.sprouttrail.image.controller;

import com.sprouttrail.image.model.EditImageRequest;
import com.sprouttrail.image.model.GenerateImageRequest;
import com.sprouttrail.image.model.GeneratedImage;
import com.sprouttrail.image.service.ImageService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
                request.effectivePrompt(), request.effectiveAspectRatio());
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

    /** 两种生成方式共用相同的预览、下载及文件名响应结构。 */
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
