package com.app.categorise.data.repository;

import com.app.categorise.data.entity.UntranscribedLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface UntranscribedLinkRepository extends JpaRepository<UntranscribedLinkEntity, UntranscribedLinkEntity.UntranscribedLinkId> {
    List<UntranscribedLinkEntity> findByUserId(UUID userId);
    void deleteByUserIdAndLink(UUID userId, String link);

    /**
     * Delete all untranscribed links belonging to a user (e.g. on account deletion).
     */
    @Modifying
    @Transactional
    void deleteByUserId(UUID userId);
}
