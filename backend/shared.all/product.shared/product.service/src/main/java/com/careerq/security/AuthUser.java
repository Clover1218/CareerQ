package com.careerq.security;


import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 会话中的登录主体。username 用 phoneHash，避免暴露手机号。
 * permissions 是 users.role 之外的额外授权（如 arbitrate），登录时固化。
 */
public class AuthUser implements UserDetails {

    private final Long id;
    private final String role;
    private final String nickname;
    private final String phoneHash;
    private final Set<String> permissions;

    public AuthUser(Long id, String role, String nickname, String phoneHash, Collection<String> permissions) {
        this.id = id;
        this.role = role;
        this.nickname = nickname;
        this.phoneHash = phoneHash;
        this.permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public static AuthUser of(Long id, String role, String nickname, String phoneHash) {
        return new AuthUser(id, role, nickname, phoneHash, Set.of());
    }

    public static AuthUser of(Long id, String role, String nickname, String phoneHash,
                              Collection<String> permissions) {
        return new AuthUser(id, role, nickname, phoneHash, permissions);
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

    public Set<String> getPermissions() {
        return permissions;
    }

    /** admin 视为拥有全部额外权限。 */
    public boolean hasPermission(String permission) {
        return "admin".equalsIgnoreCase(role)
                || (permission != null && permissions.contains(permission));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
        for (String permission : permissions) {
            authorities.add(new SimpleGrantedAuthority("PERM_" + permission.toUpperCase()));
        }
        return authorities;
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
