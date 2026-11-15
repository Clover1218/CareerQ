package com.careerq.service.impl;

import com.careerq.dao.UserDao;
import com.careerq.dataobject.UserDO;
import com.careerq.security.AuthUser;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 提供 UserDetailsService，避免 Spring Boot 生成默认内存用户与随机密码。
 * 由于本项目登录走「手机验证码 + 会话」，该实现主要用于框架装配与后续扩展。
 */
@Service
public class CareerQUserDetailsServiceImpl implements UserDetailsService {

    private final UserDao userDao;

    public CareerQUserDetailsServiceImpl(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public UserDetails loadUserByUsername(String phoneHash) throws UsernameNotFoundException {
        UserDO user = userDao.findByPhoneHash(phoneHash)
                .orElseThrow(() -> new UsernameNotFoundException("user not found"));
        return AuthUser.of(user.getId(), user.getRole(), user.getNickname(), user.getPhoneHash());
    }
}
