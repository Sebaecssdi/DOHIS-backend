package com.dohis.app.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "file_permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilePermission {

    @Id
    private String id;

    private String fileId;   // ID del archivo/carpeta en Drive
    private String userId;   // ID del usuario (User.id)

    private boolean canView;
    private boolean canEdit;
    private boolean canDelete;
}
