package com.app.categorise.api.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigurationControllerTest {

    @Test
    void returnsDisabledSubscriptionsWhenConfiguredOff() {
        var controller = new AppConfigurationController(false);

        assertFalse(controller.getConfiguration().subscriptionsEnabled());
    }

    @Test
    void returnsEnabledSubscriptionsWhenConfiguredOn() {
        var controller = new AppConfigurationController(true);

        assertTrue(controller.getConfiguration().subscriptionsEnabled());
    }
}
