package com.example.aidocumentassistant.auth;

import com.example.aidocumentassistant.config.AppProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public JwtService(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String createAccessToken(AppUser user) {
        return createToken(user, "access", Instant.now().plus(properties.jwt().accessTokenTtl()));
    }

    public String createRefreshToken(AppUser user) {
        return createToken(user, "refresh", Instant.now().plus(properties.jwt().refreshTokenTtl()));
    }

    public TokenClaims verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new BadCredentialsException("Invalid token");
            }

            String signedPayload = parts[0] + "." + parts[1];
            byte[] expected = hmacSha256(signedPayload);
            byte[] actual = URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new BadCredentialsException("Invalid token signature");
            }

            Map<String, Object> payload = objectMapper.readValue(
                    URL_DECODER.decode(parts[1]),
                    new TypeReference<>() {
                    }
            );
            long expiresAt = number(payload.get("exp"));
            if (Instant.now().getEpochSecond() >= expiresAt) {
                throw new BadCredentialsException("Token expired");
            }

            return new TokenClaims(
                    Long.parseLong(payload.get("sub").toString()),
                    payload.get("email").toString(),
                    Role.valueOf(payload.get("role").toString()),
                    payload.get("typ").toString()
            );
        } catch (BadCredentialsException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BadCredentialsException("Invalid token", exception);
        }
    }

    private String createToken(AppUser user, String type, Instant expiresAt) {
        try {
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", user.getId().toString());
            payload.put("email", user.getEmail());
            payload.put("role", user.getRole().name());
            payload.put("typ", type);
            payload.put("iat", Instant.now().getEpochSecond());
            payload.put("exp", expiresAt.getEpochSecond());

            String encodedHeader = encodeJson(header);
            String encodedPayload = encodeJson(payload);
            String signedPayload = encodedHeader + "." + encodedPayload;
            return signedPayload + "." + URL_ENCODER.encodeToString(hmacSha256(signedPayload));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create token", exception);
        }
    }

    private String encodeJson(Object value) throws Exception {
        return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private byte[] hmacSha256(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    public record TokenClaims(Long userId, String email, Role role, String tokenType) {
    }
}
