package com.sprouttrail.image.model;

/**
 * 页面允许用户选择的图片画幅
 */
public enum ImageAspectRatio {

    AUTO("auto"),
    SQUARE("1024x1024"),
    LANDSCAPE("1344x768"),
    PORTRAIT("768x1344"),
    LANDSCAPE_4_3("1152x864");

    private final String imageSize;

    ImageAspectRatio(String imageSize) {
        this.imageSize = imageSize;
    }

    /**
     * 获取发送给图片模型的尺寸参数。
     */
    public String imageSize() {
        return imageSize;
    }
}
