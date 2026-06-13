package com.dohis.app.repository;

import com.dohis.app.model.User;

import java.util.List;
import java.util.Optional;

import com.dohis.app.security.Role;
import org.springframework.data.mongodb.repository.MongoRepository;


public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    List<User> findByRole(Role role);
    List<User> findByRoleAndArea(Role role, String area);
}
