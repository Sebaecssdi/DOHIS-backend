package com.dohis.app.repository;

import com.dohis.app.model.OwnerAuth;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OwnerAuthRepository extends MongoRepository<OwnerAuth, String> {
}
