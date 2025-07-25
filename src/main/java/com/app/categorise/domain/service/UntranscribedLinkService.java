package com.app.categorise.domain.service;

import com.app.categorise.data.entity.UntranscribedLinkEntity;
import com.app.categorise.data.repository.UntranscribedLinkRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UntranscribedLinkService {
    private final UntranscribedLinkRepository repo;

    public UntranscribedLinkService(UntranscribedLinkRepository repo) {
        this.repo = repo;
    }

    public void saveLink(UUID userId, String link) {
        repo.save(new UntranscribedLinkEntity(userId, link));
    }

    public List<String> getLinks(UUID userId) {
        return repo.findByUserId(userId).stream()
            .map(UntranscribedLinkEntity::getLink)
            .collect(Collectors.toList());
    }

    public void deleteLink(UUID userId, String link) {
        repo.deleteByUserIdAndLink(userId, link);
    }
}
