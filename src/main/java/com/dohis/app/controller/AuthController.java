package com.dohis.app.controller;

import com.dohis.app.model.User;
import com.dohis.app.repository.UserRepository;
import com.dohis.app.security.JwtService;
import com.dohis.app.security.Role;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final GoogleIdTokenVerifier verifier;

    public AuthController(
            JwtService jwtService,
            UserRepository userRepository,
            @Value("${app.google.clientId}") String googleClientId
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;

        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                JacksonFactory.getDefaultInstance()
        )
                .setAudience(Collections.singletonList(googleClientId))
                .setIssuers(Arrays.asList("accounts.google.com", "https://accounts.google.com"))
                .build();
    }

    // ===== DTOs =====
    public static class GoogleAuthRequest {
        public String idToken;
        public String getIdToken() { return idToken; }
        public void setIdToken(String idToken) { this.idToken = idToken; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GoogleAuthResponse {
        public boolean ok;
        public String jwt;
        public String email;
        public String name;
        public String picture;
        public String sub;
        public String error;

        public GoogleAuthResponse ok(boolean v){ this.ok = v; return this; }
        public GoogleAuthResponse jwt(String v){ this.jwt = v; return this; }
        public GoogleAuthResponse email(String v){ this.email = v; return this; }
        public GoogleAuthResponse name(String v){ this.name = v; return this; }
        public GoogleAuthResponse picture(String v){ this.picture = v; return this; }
        public GoogleAuthResponse sub(String v){ this.sub = v; return this; }
        public GoogleAuthResponse error(String v){ this.error = v; return this; }
    }

    /**
     * POST /auth/google
     * Body: { "idToken": "<credential de GIS>" }
     * Resp: { ok, jwt, email, name, picture, sub }
     */
    @PostMapping(value = "/google", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GoogleAuthResponse> authGoogle(@RequestBody GoogleAuthRequest req) {
        try {
            String idToken = (req != null && StringUtils.hasText(req.idToken)) ? req.idToken.trim() : null;
            if (!StringUtils.hasText(idToken)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new GoogleAuthResponse().ok(false).error("idToken requerido"));
            }

            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new GoogleAuthResponse().ok(false).error("Invalid ID token"));
            }

            Payload p = token.getPayload();
            String sub     = p.getSubject();
            String email   = p.getEmail();
            String name    = (String) p.get("name");
            String picture = (String) p.get("picture");

            // === Upsert en Mongo por email ===
            Optional<User> optionalUser = userRepository.findByEmail(email);

            if (optionalUser.isEmpty()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new GoogleAuthResponse()
                                .ok(false)
                                .error("Tu cuenta (" + email + ") no está registrada en DOHIS. " +
                                        "Pide a un OWNER/ADMIN que te cree un usuario."));
            }

// Usuario existe → usar datos guardados en BD
            User user = optionalUser.get();

// (Opcional) actualizar nombre/avatar si cambió en Google
            boolean dirty = false;
            if (name != null && !name.equals(user.getName())) { user.setName(name); dirty = true; }
            if (picture != null && !picture.equals(user.getAvatar())) { user.setAvatar(picture); dirty = true; }

            if (dirty) userRepository.save(user);

            // Claims informativos (no meto "role" aquí porque JwtService lo añade)
            Map<String,Object> claims = new HashMap<>();

            // Si querés guardar el sub de Google, usá otra clave
            claims.put("googleSub", sub);   // opcional

            claims.put("email", email);
            claims.put("name", name);
            claims.put("picture", picture);
            claims.put("provider", "google");


            // Firma JWT con el rol real de BD
            String jwt = jwtService.generateToken(user.getEmail(), user.getRole(), 60, claims);

            GoogleAuthResponse resp = new GoogleAuthResponse()
                    .ok(true)
                    .jwt(jwt)
                    .email(user.getEmail())
                    .name(user.getName())
                    .picture(user.getAvatar())
                    .sub(sub);

            return ResponseEntity.ok(resp);

        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new GoogleAuthResponse().ok(false).error(ex.getMessage()));
        }
    }

    /**
     * GET /auth/me — requiere Bearer token válido.
     */
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,Object>> me(Authentication auth) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            Map<String,Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("error", "unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
        }
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("name", auth.getName());
        body.put("principal", auth.getPrincipal());
        body.put("authorities", auth.getAuthorities());
        return ResponseEntity.ok(body);
    }
}
