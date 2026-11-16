package com.careerq.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
            @Pattern(regexp = "[0-9]{6}", message = "验证码必须为 6 位数字")
            String code) {
    }

    public record RegisterRequest(
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
            String phone,
            @NotBlank(message = "验证码不能为空")
            @Pattern(regexp = "[0-9]{6}", message = "验证码必须为 6 位数字")
            String code,
            @Size(max = 64, message = "昵称不能超过 64 字")
            String nickname,
            @Size(max = 128, message = "公司名称不能超过 128 字")
            String company,
            @Size(max = 64, message = "行业不能超过 64 字")
            String industry,
            @Size(max = 64, message = "职位不能超过 64 字")
            String position,
            /** 身份：student（默认）/ hr；HR 认证流程暂缓 */
            String role) {
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
