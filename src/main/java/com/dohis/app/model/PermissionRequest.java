package com.dohis.app.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "permission_requests")
public class PermissionRequest {

    @Id
    private String id;

    private String fileId;
    private String fileName;

    // "EDITAR" o "ELIMINAR"
    private String type;

    // "PENDING" | "APPROVED" | "REJECTED"
    private String status;

    // --- solicitante ---
    private String requesterId;
    private String requesterName;
    private String requesterEmail;
    private String requesterRole;

    // --- aprobador ---
    private String approverId;
    private String approverName;
    private String approverEmail;

    private Instant createdAt;
    private Instant decidedAt;

    // true cuando el solicitante hizo "Entendido" en la notificación
    private boolean acknowledgedByRequester;
}
