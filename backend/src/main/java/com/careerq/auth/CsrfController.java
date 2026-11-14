package com.careerq.auth;

import com.careerq.common.ApiResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 给前后端分离的 SPA 提供 CSRF token。
 * 前端：GET 本接口 → 从响应取 token（同时写入 XSRF-TOKEN Cookie）→ 所有非 GET 请求带 X-XSRF-TOKEN 头。
 */
@RestController
public class CsrfController {

    @GetMapping("/api/v1/auth/csrf")
    public ApiResponse<Map<String, String>> csrf(CsrfToken token) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("token", token.getToken());
        result.put("headerName", token.getHeaderName());
        result.put("parameterName", token.getParameterName());
        return ApiResponse.ok(result);
    }
}
