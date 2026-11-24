package com.phuang.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.CodePointLength;

/** 管理员建号时提交的账号信息。 */
@Getter
@Setter
public class RegisterRequest {

    // 沿用 String.isBlank() 的空白字符判定，并允许非空白文本跨行。
    private static final String NON_BLANK = "(?s).*\\P{javaWhitespace}.*";

    @NotNull(message = "userId 必填")
    @Pattern(regexp = NON_BLANK, message = "userId 必填")
    @CodePointLength(max = 64, message = "userId 最多 64 个字符")
    private String userId;

    @NotNull(message = "username 必填")
    @Pattern(regexp = NON_BLANK, message = "username 必填")
    @CodePointLength(max = 64, message = "username 最多 64 个字符")
    private String username;

    @NotNull(message = "password 必填")
    @Pattern(regexp = NON_BLANK, message = "password 必填")
    private String password;

    @CodePointLength(max = 64, message = "realName 最多 64 个字符")
    private String realName;

    private String role;
}
