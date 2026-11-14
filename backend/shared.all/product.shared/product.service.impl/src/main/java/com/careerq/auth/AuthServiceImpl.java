package com.careerq.auth;

import com.careerq.auth.AuthDtos.LoginRequest;
import com.careerq.auth.AuthDtos.LoginResult;
import com.careerq.auth.AuthDtos.UserView;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.dao.UserDao;
import com.careerq.dataobject.UserDO;
import com.careerq.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import com.careerq.service.AuthService;
import com.careerq.service.SmsCodeService;
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
public class AuthServiceImpl implements AuthService {

    private final UserDao userDao;
    private final SmsCodeService smsCodeService;

    public AuthServiceImpl(UserDao userDao, SmsCodeService smsCodeService) {
        this.userDao = userDao;
        this.smsCodeService = smsCodeService;
    }

    @Override
    @Transactional
    public LoginResult login(LoginRequest request, HttpServletRequest httpRequest) {
        if (!smsCodeService.verify(request.phone(), request.code())) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "验证码不正确");
        }

        String phoneHash = sha256(request.phone());
        UserDO user = userDao.findByPhoneHash(phoneHash)
                .orElseGet(() -> registerStudent(request.phone(), phoneHash));

        AuthUser principal = AuthUser.of(user.getId(), user.getRole(), user.getNickname(), user.getPhoneHash());
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        httpRequest.getSession(true)
                .setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        return new LoginResult(toView(user));
    }

    @Override
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @Override
    public UserView me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser principal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userDao.findById(principal.getId())
                .map(AuthServiceImpl::toView)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private UserDO registerStudent(String phone, String phoneHash) {
        UserDO user = new UserDO();
        user.setRole("student");
        user.setPhoneHash(phoneHash);
        user.setPhoneVerifiedAt(LocalDateTime.now());
        user.setNickname("用户" + phone.substring(phone.length() - 4));
        user.setStatus("active");
        userDao.insert(user);
        return user;
    }

    private static UserView toView(UserDO user) {
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
