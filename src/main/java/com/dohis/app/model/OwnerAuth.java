package com.dohis.app.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("owner_auth")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OwnerAuth {
    @Id
    private String id;             // usa "owner" fijo
    private String refreshToken;   // guarda aquí el refresh token del dueño
    private Instant savedAt;
}
