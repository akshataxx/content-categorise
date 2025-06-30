package com.app.categorise.data.repository;

import com.app.categorise.data.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findBySub(String sub);
    Optional<UserEntity> findByEmail(String email);
}

