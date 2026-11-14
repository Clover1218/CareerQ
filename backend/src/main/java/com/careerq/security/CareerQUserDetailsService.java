package com.careerq.security;

import com.careerq.user.User;
import com.careerq.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 提供 UserDetailsService，避免 Spring Boot 生成默认内存用户与随机密码。
 * 由于本项目登录走「手机验证码 + 会话」，该实现主要用于框架装配与后续扩展。
 */
@Service
public class CareerQUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CareerQUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String phoneHash) throws UsernameNotFoundException {
        User user = userRepository.findByPhoneHash(phoneHash)
                .orElseThrow(() -> new UsernameNotFoundException("user not found"));
        return AuthUser.from(user);
    }
}
