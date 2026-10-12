package com.example.aidocumentassistant.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record AuthRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 6) String password
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record UserResponse(Long id, String email, Role role) {
        public static UserResponse from(AppUser user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getRole());
        }
    }

    public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {
    }
}
