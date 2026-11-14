package com.careerq.auth;

import com.careerq.auth.AuthDtos.LoginRequest;
import com.careerq.auth.AuthDtos.LoginResult;
import com.careerq.auth.AuthDtos.UserView;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.security.AuthUser;
import com.careerq.user.User;
import com.careerq.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 登录/登出/当前用户。手机号只存 SHA-256 哈希（MVP 不落明文）。
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final SmsCodeService smsCodeService;

    public AuthService(UserRepository userRepository, SmsCodeService smsCodeService) {
        this.userRepository = userRepository;
        this.smsCodeService = smsCodeService;
    }

    @Transactional
    public LoginResult login(LoginRequest request, HttpServletRequest httpRequest) {
        if (!smsCodeService.verify(request.phone(), request.code())) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "验证码不正确");
        }

        String phoneHash = sha256(request.phone());
        User user = userRepository.findByPhoneHash(phoneHash)
                .orElseGet(() -> registerStudent(request.phone(), phoneHash));

        AuthUser principal = AuthUser.from(user);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        httpRequest.getSession(true)
                .setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        return new LoginResult(toView(user));
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    public UserView me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser principal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userRepository.findById(principal.getId())
                .map(AuthService::toView)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private User registerStudent(String phone, String phoneHash) {
        User user = new User();
        user.setRole("student");
        user.setPhoneHash(phoneHash);
        user.setPhoneVerifiedAt(LocalDateTime.now());
        user.setNickname("用户" + phone.substring(phone.length() - 4));
        user.setStatus("active");
        return userRepository.save(user);
    }

    private static UserView toView(User user) {
        return new UserView(
                user.getId(),
                user.getRole(),
                user.getNickname(),
                user.getSchool(),
                user.getMajor(),
                user.getGrade());
    }

    static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
