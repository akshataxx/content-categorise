package com.app.categorise.data.repository;

import com.app.categorise.data.entity.CategoryAliasEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CategoryAliasRepository extends MongoRepository<CategoryAliasEntity, String> {
    List<CategoryAliasEntity> findByUserId(String userId);
}
