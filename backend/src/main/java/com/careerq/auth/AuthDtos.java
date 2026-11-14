package com.careerq.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 认证相关请求/响应 DTO。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record SmsCodeRequest(
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
            String phone) {
    }

    public record LoginRequest(
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
            String phone,
            @NotBlank(message = "验证码不能为空")
            String code) {
    }

    public record UserView(
            Long id,
            String role,
            String nickname,
            String school,
            String major,
            String grade) {
    }

    public record LoginResult(UserView user) {
    }
}
