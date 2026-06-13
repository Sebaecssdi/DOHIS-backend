package com.dohis.app.config;

import com.dohis.app.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Map<String, Object> claims = jwtService.parse(token);

                // principal: sub -> email -> fallback
                String principal = (String) claims.get("sub");
                if (principal == null) principal = (String) claims.get("email");
                if (principal == null) principal = "unknown";

                // rol tolerante (claim "role" o "roles")
                String roleClaim = null;
                Object rolesObj = claims.get("roles");
                if (rolesObj instanceof List<?> list && !list.isEmpty()) {
                    roleClaim = String.valueOf(list.get(0));
                }
                if (roleClaim == null) {
                    roleClaim = String.valueOf(claims.getOrDefault("role", "STANDARD"));
                }
                roleClaim = roleClaim == null ? "STANDARD" : roleClaim.trim().toUpperCase(Locale.ROOT);

                String authority = roleClaim.startsWith("ROLE_") ? roleClaim : "ROLE_" + roleClaim;

                var auth = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority(authority)));

                SecurityContextHolder.getContext().setAuthentication(auth);
                System.out.println("[JWT] Autenticado como: " + principal + " (" + authority + ")");
            } catch (Exception e) {
                System.out.println("[JWT] Token inválido: " + e.getMessage());
                // seguimos anónimos; rutas protegidas devolverán 401/403 si corresponde
            }
        }

        // ¡Siempre continuar la cadena!
        filterChain.doFilter(request, response);
    }
}
