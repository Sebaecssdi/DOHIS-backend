package com.dohis.app.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * HS256 con secret Base64 (32 bytes recomendado).
 * application.properties:
 *   app.jwt.secret = <openssl rand -base64 32>
 */
@Service
public class JwtService {

    private final Key key;

    public JwtService(@Value("${app.jwt.secret}") String base64Secret) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
    }

    /**
     * Genera un JWT con expiración en minutos y claim "role" = role.name().
     */
    public String generateToken(String subjectEmail,
                                Role role,
                                int expiresMinutes,
                                Map<String, Object> extraClaims) {

        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expiresMinutes * 60L);

        var builder = Jwts.builder()
                .setSubject(subjectEmail)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp));

        if (extraClaims != null && !extraClaims.isEmpty()) {
            builder.addClaims(extraClaims);
        }

        // claim de rol textual (OWNER, ADMIN, AREA_LEAD, STANDARD)
        builder.claim("role", role.name());

        return builder
                .signWith(key) // HS256
                .compact();
    }

    /**
     * Overload de compatibilidad (si en algún sitio lo llamabas así):
     * 24h y Role.STANDARD por defecto.
     */
    public String generateToken(Map<String, Object> extraClaims, String subject) {
        return generateToken(subject, Role.STANDARD, 1440, extraClaims);
    }

    /**
     * Valida el token y devuelve los claims (lanza excepción si inválido/expirado).
     */
    public Map<String, Object> parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
