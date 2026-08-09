package com.app.categorise.api.dto;

/**
 * Public, non-sensitive feature configuration consumed by client apps.
 */
public record AppConfigurationDto(boolean subscriptionsEnabled) {
}
