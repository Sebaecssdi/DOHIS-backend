package com.dohis.app.repository;

import com.dohis.app.model.FilePermission;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FilePermissionRepository extends MongoRepository<FilePermission, String> {

    List<FilePermission> findByFileId(String fileId);

    void deleteByFileId(String fileId);
    List<FilePermission> findByUserId(String userId);
}
