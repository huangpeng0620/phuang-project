package com.sprouttrail.image.service;

import com.sprouttrail.image.model.response.GeneratedImage;
import com.sprouttrail.image.model.response.ImageHistoryResponse;
import com.sprouttrail.image.model.enums.ImageAspectRatio;
import org.springframework.web.multipart.MultipartFile;

/**
 * 定义图片生成与图片编辑的业务操作。
 */
public interface ImageService {

    /**
     * 根据文字描述生成图片，并将生成结果保存到 MinIO。
     *
     * @param originalPrompt 用户输入的原始提示词
     * @param optimizedPrompt 实际提交给图片模型的提示词
     * @param aspectRatio 用户选择的图片画幅
     * @return 图片文件名、二进制内容及 MinIO 地址
     */
    GeneratedImage generate(String originalPrompt, String optimizedPrompt, ImageAspectRatio aspectRatio);

    /**
     * 根据用户上传的原图和编辑要求生成优化后的图片，并将结果保存到 MinIO。
     *
     * @param upload 用户上传的原图
     * @param prompt 用户确认的图片编辑要求
     * @param aspectRatio 用户选择的输出画幅
     * @return 图片文件名、二进制内容及 MinIO 地址
     */
    GeneratedImage edit(MultipartFile upload, String prompt, ImageAspectRatio aspectRatio);

    /** 查询指定用户的文生图历史记录。 */
    ImageHistoryResponse getHistory(Long userId, int page, int pageSize);

    /** 下载当前用户拥有的历史图片。 */
    GeneratedImage downloadHistoryImage(Long userId, Long recordId);
}
