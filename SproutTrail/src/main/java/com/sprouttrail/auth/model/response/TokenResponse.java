package com.sprouttrail.auth.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 查询当前登录会话时返回的信息。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

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
}
