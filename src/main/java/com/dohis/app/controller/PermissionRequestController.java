package com.dohis.app.controller;

import com.dohis.app.dto.PermissionRequestDto;
import com.dohis.app.model.PermissionRequest;
import com.dohis.app.model.User;
import com.dohis.app.repository.PermissionRequestRepository;
import com.dohis.app.repository.UserRepository;
import com.dohis.app.security.Role;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/permission-requests")
public class PermissionRequestController {

    private final UserRepository userRepository;
    private final PermissionRequestRepository permissionRequestRepository;

    public PermissionRequestController(UserRepository userRepository,
                                       PermissionRequestRepository permissionRequestRepository) {
        this.userRepository = userRepository;
        this.permissionRequestRepository = permissionRequestRepository;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User currentUser(Authentication auth) {
        if (auth == null || !StringUtils.hasText(auth.getName())) {
            throw new RuntimeException("Usuario no autenticado");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + auth.getName()));
    }

    // -------------------------------------------------------------------------
    // DTOs internos para respuestas
    // -------------------------------------------------------------------------

    public static class ApproverDto {
        public String id;
        public String name;
        public String email;
        public String role;

        public ApproverDto(User u) {
            this.id = u.getId();
            this.name = u.getName();
            this.email = u.getEmail();
            this.role = (u.getRole() != null) ? u.getRole().name() : null;
        }
    }

    public static class RequestInboxDto {
        public String id;
        public String fileId;
        public String fileName;
        public String type;      // "EDITAR" / "ELIMINAR"
        public String createdAt;
        public String requesterName;
        public String requesterEmail;
        public String requesterRole;

        public RequestInboxDto(PermissionRequest r) {
            this.id = r.getId();
            this.fileId = r.getFileId();
            this.fileName = r.getFileName();
            this.type = r.getType();
            this.createdAt = (r.getCreatedAt() != null) ? r.getCreatedAt().toString() : null;
            this.requesterName = r.getRequesterName();
            this.requesterEmail = r.getRequesterEmail();
            this.requesterRole = r.getRequesterRole();
        }
    }

    public static class NotificationDto {
        public String id;
        public String fileId;
        public String fileName;
        public String type;          // "EDITAR"/"ELIMINAR"
        public String decision;      // "APPROVED"/"REJECTED"
        public String decidedByName;
        public String decidedByEmail;
        public String createdAt;     // usamos decidedAt o createdAt

        public NotificationDto(PermissionRequest r) {
            this.id = r.getId();
            this.fileId = r.getFileId();
            this.fileName = r.getFileName();
            this.type = r.getType();
            this.decision = r.getStatus(); // "APPROVED" o "REJECTED"
            this.decidedByName = r.getApproverName();
            this.decidedByEmail = r.getApproverEmail();
            Instant ts = (r.getDecidedAt() != null) ? r.getDecidedAt() : r.getCreatedAt();
            this.createdAt = (ts != null) ? ts.toString() : null;
        }
    }

    // -------------------------------------------------------------------------
    // 1) /approvers  -> lista de jefes posibles según tu rol
    // -------------------------------------------------------------------------

    /**
     * STANDARD  -> AREA_LEAD de su misma área (si no hay, ADMIN).
     * AREA_LEAD -> ADMIN (si no hay, OWNER).
     * ADMIN     -> OWNER.
     * OWNER     -> lista vacía (el front no muestra "Solicitar permisos").
     */
    @GetMapping("/approvers")
    @PreAuthorize("hasAnyRole('STANDARD','AREA_LEAD','ADMIN','OWNER')")
    public ResponseEntity<?> getApprovers(Authentication auth) {
        try {
            User me = currentUser(auth);
            Role role = me.getRole();
            if (role == null) {
                return ResponseEntity.ok(List.of());
            }

            List<User> approvers = List.of();

            switch (role) {
                case STANDARD -> {
                    String area = me.getArea();
                    if (StringUtils.hasText(area)) {
                        // AREA_LEAD de la misma área
                        approvers = userRepository.findByRoleAndArea(Role.AREA_LEAD, area);
                    }
                    // Si no hay leads en el área, escalamos a ADMIN
                    if (approvers == null || approvers.isEmpty()) {
                        approvers = userRepository.findByRole(Role.ADMIN);
                    }
                }
                case AREA_LEAD -> {
                    approvers = userRepository.findByRole(Role.ADMIN);
                    if (approvers == null || approvers.isEmpty()) {
                        approvers = userRepository.findByRole(Role.OWNER);
                    }
                }
                case ADMIN -> {
                    approvers = userRepository.findByRole(Role.OWNER);
                }
                case OWNER -> {
                    approvers = List.of();
                }
            }

            if (approvers == null) {
                approvers = List.of();
            }

            List<ApproverDto> dtoList = approvers.stream()
                    .map(ApproverDto::new)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dtoList);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                    .status(500)
                    .body(Map.of(
                            "ok", false,
                            "error", e.getClass().getSimpleName(),
                            "message", e.getMessage()
                    ));
        }
    }

    // -------------------------------------------------------------------------
    // 2) POST /permission-requests -> crear solicitud
    // -------------------------------------------------------------------------

    @PostMapping
    @PreAuthorize("hasAnyRole('STANDARD','AREA_LEAD','ADMIN','OWNER')")
    public ResponseEntity<?> createRequest(@RequestBody PermissionRequestDto dto,
                                           Authentication auth) {
        try {
            User me = currentUser(auth);

            if (!StringUtils.hasText(dto.getFileId())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("ok", false, "message", "fileId es obligatorio"));
            }
            if (!StringUtils.hasText(dto.getType())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("ok", false, "message", "type es obligatorio"));
            }
            if (!StringUtils.hasText(dto.getApproverId())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("ok", false, "message", "approverId es obligatorio"));
            }

            // normalizamos tipo
            String type = dto.getType().toUpperCase();
            if (!type.equals("EDITAR") && !type.equals("ELIMINAR")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("ok", false, "message", "type debe ser EDITAR o ELIMINAR"));
            }

            User approver = userRepository.findById(dto.getApproverId())
                    .orElseThrow(() -> new RuntimeException("Aprobador no encontrado"));

            PermissionRequest pr = new PermissionRequest();
            pr.setFileId(dto.getFileId());
            pr.setFileName(dto.getFileName());
            pr.setType(type); // "EDITAR" / "ELIMINAR"
            pr.setStatus("PENDING");

            pr.setRequesterId(me.getId());
            pr.setRequesterName(me.getName());
            pr.setRequesterEmail(me.getEmail());
            pr.setRequesterRole(me.getRole() != null ? me.getRole().name() : null);

            pr.setApproverId(approver.getId());
            pr.setApproverName(approver.getName());
            pr.setApproverEmail(approver.getEmail());

            pr.setCreatedAt(Instant.now());
            pr.setAcknowledgedByRequester(false);

            PermissionRequest saved = permissionRequestRepository.save(pr);

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "id", saved.getId()
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                    .status(500)
                    .body(Map.of(
                            "ok", false,
                            "error", e.getClass().getSimpleName(),
                            "message", e.getMessage()
                    ));
        }
    }

    // -------------------------------------------------------------------------
    // 3) GET /permission-requests/inbox -> solicitudes PENDING donde yo soy aprobador
    // -------------------------------------------------------------------------

    @GetMapping("/inbox")
    @PreAuthorize("hasAnyRole('AREA_LEAD','ADMIN','OWNER')")
    public ResponseEntity<?> inbox(Authentication auth) {
        try {
            User me = currentUser(auth);

            List<PermissionRequest> pending =
                    permissionRequestRepository.findByApproverIdAndStatus(me.getId(), "PENDING");

            List<RequestInboxDto> dtoList = pending.stream()
                    .map(RequestInboxDto::new)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dtoList);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                    .status(500)
                    .body(Map.of(
                            "ok", false,
                            "error", e.getClass().getSimpleName(),
                            "message", e.getMessage()
                    ));
        }
    }

    // -------------------------------------------------------------------------
    // 4) POST /permission-requests/{id}/approve  /reject
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('AREA_LEAD','ADMIN','OWNER')")
    public ResponseEntity<?> approveRequest(@PathVariable String id,
                                            Authentication auth) {
        return decideOnRequestInternal(id, true, auth);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('AREA_LEAD','ADMIN','OWNER')")
    public ResponseEntity<?> rejectRequest(@PathVariable String id,
                                           Authentication auth) {
        return decideOnRequestInternal(id, false, auth);
    }

    private ResponseEntity<?> decideOnRequestInternal(String id, boolean approve, Authentication auth) {
        try {
            User me = currentUser(auth);

            PermissionRequest r = permissionRequestRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

            if (!me.getId().equals(r.getApproverId())) {
                return ResponseEntity.status(403)
                        .body(Map.of("ok", false, "message", "No eres aprobador de esta solicitud"));
            }

            if (!"PENDING".equals(r.getStatus())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("ok", false, "message", "La solicitud ya fue resuelta"));
            }

            r.setStatus(approve ? "APPROVED" : "REJECTED");
            r.setDecidedAt(Instant.now());

            PermissionRequest saved = permissionRequestRepository.save(r);

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "status", saved.getStatus()
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                    .status(500)
                    .body(Map.of(
                            "ok", false,
                            "error", e.getClass().getSimpleName(),
                            "message", e.getMessage()
                    ));
        }
    }

    // -------------------------------------------------------------------------
    // 5) GET /permission-requests/my-notifications
    //    -> notificaciones para el solicitante (aprobadas/rechazadas y no "ack")
    // -------------------------------------------------------------------------

    @GetMapping("/my-notifications")
    @PreAuthorize("hasAnyRole('STANDARD','AREA_LEAD','ADMIN','OWNER')")
    public ResponseEntity<?> myNotifications(Authentication auth) {
        try {
            User me = currentUser(auth);

            List<PermissionRequest> list =
                    permissionRequestRepository
                            .findByRequesterIdAndAcknowledgedByRequesterFalseAndStatusNot(
                                    me.getId(), "PENDING"
                            );

            List<NotificationDto> dtoList = list.stream()
                    .map(NotificationDto::new)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dtoList);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                    .status(500)
                    .body(Map.of(
                            "ok", false,
                            "error", e.getClass().getSimpleName(),
                            "message", e.getMessage()
                    ));
        }
    }

    // -------------------------------------------------------------------------
    // 6) POST /permission-requests/notifications/{id}/ack
    //    -> marcar como "Entendido" la notificación
    // -------------------------------------------------------------------------

    @PostMapping("/notifications/{id}/ack")
    @PreAuthorize("hasAnyRole('STANDARD','AREA_LEAD','ADMIN','OWNER')")
    public ResponseEntity<?> ackNotification(@PathVariable String id,
                                             Authentication auth) {
        try {
            User me = currentUser(auth);

            PermissionRequest r = permissionRequestRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

            if (!me.getId().equals(r.getRequesterId())) {
                return ResponseEntity.status(403)
                        .body(Map.of("ok", false, "message", "No eres el solicitante de esta petición"));
            }

            r.setAcknowledgedByRequester(true);
            permissionRequestRepository.save(r);

            return ResponseEntity.ok(Map.of("ok", true));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                    .status(500)
                    .body(Map.of(
                            "ok", false,
                            "error", e.getClass().getSimpleName(),
                            "message", e.getMessage()
                    ));
        }
    }
}
