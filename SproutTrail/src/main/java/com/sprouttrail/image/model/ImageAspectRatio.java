package com.sprouttrail.image.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    /** 查询当前登录会话时返回的信息。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class TokenResponse {

        /** 后续请求携带 token 时使用的请求头名称。 */
        private String tokenName;

        /** 当前请求携带并通过校验的 token。 */
        private String tokenValue;

        /** 当前登录用户 ID。 */
        private Long userId;

        /** 当前登录用户名。 */
        private String username;

        /** token 剩余有效期，单位为秒。 */
        private Long tokenTimeout;

        public TokenResponse(String tokenName, String tokenValue, Long userId,
                             String username, Long tokenTimeout) {
            this.tokenName = tokenName;
            this.tokenValue = tokenValue;
            this.userId = userId;
            this.username = username;
            this.tokenTimeout = tokenTimeout;
        }
    }
}
