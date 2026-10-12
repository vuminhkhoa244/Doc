package com.example.aidocumentassistant.security;

import com.example.aidocumentassistant.auth.AppUser;
import com.example.aidocumentassistant.auth.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class AuthenticatedUser implements UserDetails {
    private final Long id;
    private final String email;
    private final Role role;

    public AuthenticatedUser(Long id, String email, Role role) {
        this.id = id;
        this.email = email;
        this.role = role;
    }

    public static AuthenticatedUser from(AppUser user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
    }

    public Long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public Role role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }
}
