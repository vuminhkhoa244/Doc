package com.example.aidocumentassistant.auth;

import com.example.aidocumentassistant.security.AuthenticatedUser;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleTokenVerifier googleTokenVerifier;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                          GoogleTokenVerifier googleTokenVerifier) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    @GetMapping("/me")
    public AuthDtos.UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        AppUser user = users.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return AuthDtos.UserResponse.from(user);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.AuthRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        AppUser user = users.save(new AppUser(email, passwordEncoder.encode(request.password())));
        return tokenResponse(user);
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.AuthRequest request) {
        AppUser user = users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return tokenResponse(user);
    }

    @PostMapping("/refresh")
    public AuthDtos.AuthResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        JwtService.TokenClaims claims = jwtService.verify(request.refreshToken());
        if (!"refresh".equals(claims.tokenType())) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        AppUser user = users.findById(claims.userId())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        return tokenResponse(user);
    }

    @PostMapping("/google")
    public AuthDtos.AuthResponse googleLogin(@Valid @RequestBody AuthDtos.GoogleLoginRequest request) {
        if (!googleTokenVerifier.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in is not configured");
        }

        GoogleIdToken.Payload payload;
        try {
            payload = googleTokenVerifier.verify(request.idToken());
        } catch (Exception exception) {
            throw new BadCredentialsException("Invalid Google ID token");
        }

        String email = normalizeEmail(payload.getEmail());
        String googleId = payload.getSubject();
        String displayName = (String) payload.get("name");
        String avatarUrl = (String) payload.get("picture");

        AppUser user = users.findByEmailIgnoreCase(email)
                .or(() -> users.findByGoogleId(googleId))
                .orElseGet(() -> users.save(AppUser.googleUser(email, googleId, displayName, avatarUrl)));

        if (user.getGoogleId() == null) {
            user.setGoogleId(googleId);
            if (user.getDisplayName() == null) {
                user.setDisplayName(displayName);
            }
            if (user.getAvatarUrl() == null) {
                user.setAvatarUrl(avatarUrl);
            }
            users.save(user);
        }

        return tokenResponse(user);
    }

    private AuthDtos.AuthResponse tokenResponse(AppUser user) {
        return new AuthDtos.AuthResponse(
                jwtService.createAccessToken(user),
                jwtService.createRefreshToken(user),
                AuthDtos.UserResponse.from(user)
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
