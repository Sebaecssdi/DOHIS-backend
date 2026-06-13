package com.dohis.app.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class DiagnosticsController {

    // Texto plano (si esto sale vacío, algo borra el body a nivel filtro/infra)
    @GetMapping(value = "/__whoami_txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> whoamiTxt(Authentication auth) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body("unauthorized");
        }
        return ResponseEntity.ok("user=" + auth.getName());
    }

    // JSON (sin Map.of para evitar NPE)
    @GetMapping(value = "/__whoami_json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,Object>> whoamiJson(Authentication auth) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            Map<String,Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("error", "unauthorized");
            return ResponseEntity.status(401).body(err);
        }
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("name", auth.getName());
        body.put("principal", auth.getPrincipal());
        body.put("authorities", auth.getAuthorities());
        return ResponseEntity.ok(body);
    }
}
