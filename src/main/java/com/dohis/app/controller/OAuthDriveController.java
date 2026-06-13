package com.dohis.app.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/oauth/drive")
public class OAuthDriveController {

    private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    @Value("${app.google.clientId}")
    private String clientId;

    @Value("${app.google.clientSecret}")
    private String clientSecret;

    @Value("${app.google.redirectUri}")
    private String redirectUri;

    // 1) Devuelve la URL de consentimiento
    @GetMapping("/connect")
    public ResponseEntity<?> connect() throws Exception {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // Scopes que necesitas (Drive completo o ajusta según tu diseño)
        List<String> scopes = List.of("https://www.googleapis.com/auth/drive");

        // access_type=offline → refresh token
        // prompt=consent      → fuerza re-consent (evita que Google omita el refresh)
        String authUrl = new GoogleAuthorizationCodeRequestUrl(clientId, redirectUri, scopes)
                .setAccessType("offline")
                .set("prompt","consent")
                .build();

        // (opcional) agrega include_granted_scopes=true
        authUrl += "&include_granted_scopes=true";

        // (opcional) estado CSRF
        String state = URLEncoder.encode("dohis_state", StandardCharsets.UTF_8);
        authUrl += "&state=" + state;

        return ResponseEntity.ok(Map.of("authUrl", authUrl));
    }

    // 2) Canjea el code por tokens y devuelve refreshToken
    @GetMapping("/callback")
    public ResponseEntity<?> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false, name = "error") String oauthError
    ) throws Exception {
        if (oauthError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", oauthError));
        }
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing_code"));
        }

        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                httpTransport,
                JSON_FACTORY,
                clientId,
                clientSecret,
                code,
                redirectUri
        ).execute();

        String accessToken = tokenResponse.getAccessToken();
        String refreshToken = tokenResponse.getRefreshToken(); // Puede venir null si Google decide no emitir otro

        return ResponseEntity.ok(Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken
        ));
    }
}
