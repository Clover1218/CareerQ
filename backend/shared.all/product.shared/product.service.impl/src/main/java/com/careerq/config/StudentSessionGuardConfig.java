package com.careerq.config;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 校验学生页面发起写操作时所见的身份；真实身份始终来自服务端会话。 */
@Configuration
public class StudentSessionGuardConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()) || "OPTIONS".equals(request.getMethod())) return true;
                String expected = request.getHeader("X-CareerQ-Student-Id");
                if (expected == null) return true;
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) return true;
                if (!"student".equals(user.getRole()) || !user.getId().toString().equals(expected)) {
                    throw new BusinessException(ErrorCode.SESSION_CHANGED);
                }
                return true;
            }
        }).addPathPatterns("/api/v1/**").excludePathPatterns("/api/v1/auth/**");
    }
}
