package com.app.categorise.api.controller;

import com.app.categorise.api.dto.AppConfigurationDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes public feature switches that let released clients adapt without
 * embedding environment-specific settings in the app bundle.
 */
@RestController
@RequestMapping("/api/app-config")
public class AppConfigurationController {

    private final boolean subscriptionsEnabled;

    public AppConfigurationController(
            @Value("${app.subscriptions.enabled:false}") boolean subscriptionsEnabled
    ) {
        this.subscriptionsEnabled = subscriptionsEnabled;
    }

    @GetMapping
    public AppConfigurationDto getConfiguration() {
        return new AppConfigurationDto(subscriptionsEnabled);
    }
}
