package com.careerq.service;

import com.careerq.auth.AuthDtos.LoginRequest;
import com.careerq.auth.AuthDtos.LoginResult;
import com.careerq.auth.AuthDtos.UserView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

public interface AuthService {

    LoginResult login(LoginRequest request, HttpServletRequest httpRequest);

    void logout(HttpServletRequest request);

    UserView me(Authentication authentication);
}
