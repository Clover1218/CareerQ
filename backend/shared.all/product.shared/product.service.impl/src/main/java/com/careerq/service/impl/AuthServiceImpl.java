package com.careerq.service.impl;

import com.careerq.auth.AuthDtos.LoginRequest;
import com.careerq.auth.AuthDtos.LoginResult;
import com.careerq.auth.AuthDtos.RegisterRequest;
import com.careerq.auth.AuthDtos.UserView;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.dao.HrProfileDao;
import com.careerq.dao.UserDao;
import com.careerq.dao.UserPermissionDao;
import com.careerq.dataobject.HrProfileDO;
import com.careerq.dataobject.UserDO;
import com.careerq.security.AuthUser;
import com.careerq.service.AuthService;
import com.careerq.service.SmsCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DuplicateKeyException;
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
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 登录 / 注册 / 登出 / 当前用户。
 * 注册规则：
 * - 学生：手机号 + 验证码即可，注册时选填的学校、专业、年级一并保存；
 * - HR：手机号 + 验证码 + 就职公司（行业/职位可选），同时写入 hr_profiles（认证状态 pending，真实认证暂缓）。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserDao userDao;
    private final SmsCodeService smsCodeService;
    private final HrProfileDao hrProfileDao;
    private final UserPermissionDao userPermissionDao;

    public AuthServiceImpl(UserDao userDao, SmsCodeService smsCodeService, HrProfileDao hrProfileDao,
                           UserPermissionDao userPermissionDao) {
        this.userDao = userDao;
        this.smsCodeService = smsCodeService;
        this.hrProfileDao = hrProfileDao;
        this.userPermissionDao = userPermissionDao;
    }

    @Override
    public LoginResult login(LoginRequest request, HttpServletRequest httpRequest) {
        verifyCode(request.phone(), request.code());
        String phoneHash = sha256(request.phone());
        UserDO user = userDao.findByPhoneHash(phoneHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_REGISTERED));
        if (!"active".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该账号当前不可登录");
        }
        Set<String> permissions = establishSession(user, httpRequest);
        return new LoginResult(toView(user, permissions));
    }

    @Override
    @Transactional
    public LoginResult register(RegisterRequest request, HttpServletRequest httpRequest) {
        verifyCode(request.phone(), request.code());
        String role = normalizeRegisterRole(request.role());
        String company = blankToNull(request.company());
        if ("hr".equals(role) && company == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "HR 注册需要填写就职公司");
        }

        String phoneHash = sha256(request.phone());
        UserDO user = new UserDO();
        user.setRole(role);
        user.setPhoneHash(phoneHash);
        user.setPhoneVerifiedAt(LocalDateTime.now());
        user.setNickname(defaultNickname(request.nickname(), request.phone()));
        if ("student".equals(role)) {
            user.setSchool(blankToNull(request.school()));
            user.setMajor(blankToNull(request.major()));
            user.setGrade(blankToNull(request.grade()));
        }
        user.setStatus("active");
        try {
            userDao.insert(user);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }

        if ("hr".equals(role)) {
            HrProfileDO profile = new HrProfileDO();
            profile.setUserId(user.getId());
            profile.setCompanyDisplay(company);
            profile.setIndustry(blankToNull(request.industry()));
            profile.setPosition(blankToNull(request.position()));
            profile.setVerifyStatus("pending");
            hrProfileDao.insert(profile);
        }

        Set<String> permissions = establishSession(user, httpRequest);
        return new LoginResult(toView(user, permissions));
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
                .map(user -> toView(user, principal.getPermissions()))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private void verifyCode(String phone, String code) {
        if (!smsCodeService.verify(phone, code)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "验证码不正确");
        }
    }

    private Set<String> establishSession(UserDO user, HttpServletRequest httpRequest) {
        Set<String> permissions = loadPermissions(user.getId());
        AuthUser principal =
                AuthUser.of(user.getId(), user.getRole(), user.getNickname(), user.getPhoneHash(), permissions);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());

        HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            session = httpRequest.getSession(true);
        } else {
            httpRequest.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return permissions;
    }

    private Set<String> loadPermissions(Long userId) {
        return new TreeSet<>(userPermissionDao.findPermissions(userId));
    }

    private static String normalizeRegisterRole(String role) {
        if (role == null || role.isBlank()) {
            return "student";
        }
        String value = role.trim().toLowerCase();
        if (!"student".equals(value) && !"hr".equals(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "role 只能是 student 或 hr");
        }
        return value;
    }

    private static String defaultNickname(String nickname, String phone) {
        String value = blankToNull(nickname);
        return value != null ? value : "用户" + phone.substring(phone.length() - 4);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static UserView toView(UserDO user, Collection<String> permissions) {
        return new UserView(
                user.getId(),
                user.getRole(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getSchool(),
                user.getMajor(),
                user.getGrade(),
                List.copyOf(permissions));
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
