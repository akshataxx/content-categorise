package com.app.categorise.data.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "untranscribed_links")
@IdClass(UntranscribedLinkEntity.UntranscribedLinkId.class)
public class UntranscribedLinkEntity {
    @Id
    private UUID userId;

    @Id
    private String link;

    public UntranscribedLinkEntity() {}

    public UntranscribedLinkEntity(UUID userId, String link) {
        this.userId = userId;
        this.link = link;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public static class UntranscribedLinkId implements Serializable {
        private UUID userId;
        private String link;

        public UntranscribedLinkId() {}

        public UntranscribedLinkId(UUID userId, String link) {
            this.userId = userId;
            this.link = link;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UntranscribedLinkId that = (UntranscribedLinkId) o;
            return Objects.equals(userId, that.userId) &&
                Objects.equals(link, that.link);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, link);
        }
    }
}
