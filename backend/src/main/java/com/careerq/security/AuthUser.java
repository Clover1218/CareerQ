package com.careerq.security;

import com.careerq.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 会话中的登录主体。username 用 phoneHash，避免暴露手机号。
 */
public class AuthUser implements UserDetails {

    private final Long id;
    private final String role;
    private final String nickname;
    private final String phoneHash;

    public AuthUser(Long id, String role, String nickname, String phoneHash) {
        this.id = id;
        this.role = role;
        this.nickname = nickname;
        this.phoneHash = phoneHash;
    }

    public static AuthUser from(User user) {
        return new AuthUser(user.getId(), user.getRole(), user.getNickname(), user.getPhoneHash());
    }

    public Long getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public String getNickname() {
        return nickname;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return phoneHash;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
