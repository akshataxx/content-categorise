package com.app.categorise.api.dto;

import java.util.UUID;

/**
 * Response body for device token registration.
 * Matches contracts/device-api.yaml.
 * registered=true if new registration, false if existing token was updated.
 */
public record DeviceRegistrationResponse(
        UUID id,
        boolean registered
) {}
