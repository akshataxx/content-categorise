package com.app.categorise.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AppleAuthRequest {
    
    @NotBlank(message = "Identity token is required")
    private String identityToken;
    
    @NotBlank(message = "User identifier is required")
    private String userIdentifier;
    
    private String email;
    private String firstName;
    private String lastName;
}
