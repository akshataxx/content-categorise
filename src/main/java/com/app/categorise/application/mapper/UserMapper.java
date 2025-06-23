package com.app.categorise.application.mapper;

import com.app.categorise.data.entity.UserEntity;
import com.app.categorise.domain.model.User;
import org.springframework.stereotype.Component;
@Component
public class UserMapper {
    public User toDomain(UserEntity entity) {
        User user = new User();
        user.setId(entity.getId());
        user.setUsername(entity.getUsername());
        user.setEmail(entity.getEmail());
        user.setDisplayName(entity.getDisplayName());
        return user;
    }

    public UserEntity toEntity(UserEntity domain) {
        UserEntity entity = new UserEntity();
        entity.setId(domain.getId());
        entity.setUsername(domain.getUsername());
        entity.setEmail(domain.getEmail());
        entity.setDisplayName(domain.getDisplayName());
        return entity;
    }
}