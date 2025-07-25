package com.app.categorise.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for adding an untranscribed link.
 */
public class AddLinkRequest {

    @NotNull(message = "userId is required")
    private UUID userId;

    @NotBlank(message = "link cannot be blank")
    private String link;

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
}
