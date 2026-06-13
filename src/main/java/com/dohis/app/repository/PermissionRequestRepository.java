package com.dohis.app.repository;

import com.dohis.app.model.PermissionRequest;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PermissionRequestRepository extends MongoRepository<PermissionRequest, String> {

    // Buzón del aprobador (solicitudes pendientes)
    List<PermissionRequest> findByApproverIdAndStatus(String approverId, String status);

    // Notificaciones para el solicitante (decididas, pero no marcadas como leídas)
    List<PermissionRequest> findByRequesterIdAndAcknowledgedByRequesterFalseAndStatusNot(
            String requesterId,
            String status
    );
}
