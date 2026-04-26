package com.app.categorise.application.mapper;

import com.app.categorise.api.dto.UserSubcategoryDto;
import com.app.categorise.data.entity.UserSubcategoryEntity;
import org.springframework.stereotype.Component;

@Component
public class UserSubcategoryMapper {

    public UserSubcategoryDto toDto(UserSubcategoryEntity entity) {
        return new UserSubcategoryDto(
            entity.getId(),
            entity.getParentId(),
            entity.getName(),
            entity.getDescription(),
            entity.getCreatedAt()
        );
    }
}
