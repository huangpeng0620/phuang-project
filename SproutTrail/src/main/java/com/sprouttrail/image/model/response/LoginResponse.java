package com.sprouttrail.image.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 登录成功后返回给浏览器的会话信息。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** 后续请求携带 token 时使用的请求头名称。 */
    private String tokenName;

    /** Sa-Token 登录凭证。 */
    private String tokenValue;

    /** 当前登录用户 ID。 */
    private Long userId;

    /** 当前登录用户名。 */
    private String username;

    /** token 剩余有效期，单位为秒。 */
    private Long tokenTimeout;
}
