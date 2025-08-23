package com.app.categorise.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for adding an untranscribed link.
 * The user is identified through authentication.
 */
public class AddLinkRequest {

    @NotBlank(message = "link cannot be blank")
    private String link;

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}
