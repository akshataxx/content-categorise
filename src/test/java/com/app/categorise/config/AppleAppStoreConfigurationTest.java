package com.app.categorise.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AppleAppStoreConfigurationTest {

    @Test
    void defaultsToTheAppStoreMonthlyProductId() {
        AppleAppStoreConfiguration configuration = new AppleAppStoreConfiguration();

        assertEquals("scoop", configuration.getMonthlyProductId());
    }
}
