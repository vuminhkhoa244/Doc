package com.example.aidocumentassistant.auth;

import com.example.aidocumentassistant.config.AppProperties;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
public class GoogleTokenVerifier {
    private final AppProperties properties;
    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(AppProperties properties) {
        this.properties = properties;
        AppProperties.Google google = properties.google();
        if (google == null || !google.enabled()) {
            this.verifier = null;
        } else {
            this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(google.clientId()))
                    .build();
        }
    }

    public boolean enabled() {
        return verifier != null;
    }

    public GoogleIdToken.Payload verify(String idToken) {
        if (verifier == null) {
            throw new IllegalStateException("Google sign-in is not configured");
        }
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new IllegalArgumentException("Invalid Google ID token");
            }
            return token.getPayload();
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalArgumentException("Invalid Google ID token", exception);
        }
    }
}
