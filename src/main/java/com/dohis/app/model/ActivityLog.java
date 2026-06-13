package com.dohis.app.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;


@Document("activity_logs")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ActivityLog {
    @Id private String id;
    private String userEmail;
    private String action; // upload, create_folder, list, delete
    private String targetId; // drive file/folder id
    private String description;
    private Instant at;
}