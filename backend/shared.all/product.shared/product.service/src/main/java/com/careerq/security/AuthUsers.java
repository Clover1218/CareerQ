package com.careerq.security;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import org.springframework.security.core.Authentication;

public final class AuthUsers {

    private AuthUsers() {
    }

    public static AuthUser require(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    public static AuthUser optional(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        return null;
    }
}
