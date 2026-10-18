package com.example.aidocumentassistant.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = true)
    private String password;

    @Column(nullable = true, unique = true)
    private String googleId;

    @Column(nullable = true)
    private String displayName;

    @Column(nullable = true)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected AppUser() {
    }

    public AppUser(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public static AppUser googleUser(String email, String googleId, String displayName, String avatarUrl) {
        AppUser user = new AppUser(email, null);
        user.googleId = googleId;
        user.displayName = displayName;
        user.avatarUrl = avatarUrl;
        return user;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
