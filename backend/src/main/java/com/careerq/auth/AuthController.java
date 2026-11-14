package com.careerq.auth;

import com.careerq.auth.AuthDtos.LoginRequest;
import com.careerq.auth.AuthDtos.LoginResult;
import com.careerq.auth.AuthDtos.SmsCodeRequest;
import com.careerq.auth.AuthDtos.UserView;
import com.careerq.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final SmsCodeService smsCodeService;
    private final AuthService authService;

    public AuthController(SmsCodeService smsCodeService, AuthService authService) {
        this.smsCodeService = smsCodeService;
        this.authService = authService;
    }

    @PostMapping("/auth/sms-code")
    public ApiResponse<Void> sendSmsCode(@Valid @RequestBody SmsCodeRequest request) {
        smsCodeService.send(request.phone());
        return ApiResponse.ok();
    }

    @PostMapping("/auth/login")
    public ApiResponse<LoginResult> login(@Valid @RequestBody LoginRequest request,
                                          HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request, httpRequest));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(HttpServletRequest httpRequest) {
        authService.logout(httpRequest);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me(Authentication authentication) {
        return ApiResponse.ok(authService.me(authentication));
    }
}
