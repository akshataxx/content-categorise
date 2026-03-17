package com.app.categorise.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for device token registration.
 * Matches contracts/device-api.yaml.
 */
public record DeviceRegistrationRequest(
        @NotBlank(message = "platform is required")
        @Pattern(regexp = "^(IOS|ANDROID)$", message = "platform must be IOS or ANDROID")
        String platform,

        @NotBlank(message = "fcmToken is required")
        String fcmToken,

        @NotBlank(message = "deviceId is required")
        String deviceId
) {}
