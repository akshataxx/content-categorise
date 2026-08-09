package com.app.categorise.application.internal;

import com.app.categorise.config.AppleAppStoreConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppleAppStoreBillingServiceImplTest {

    @Mock private Environment environment;

    private AppleAppStoreConfiguration config;

    @BeforeEach
    void setUp() {
        config = new AppleAppStoreConfiguration();
        config.setBundleId("com.example.app");
        config.setIssuerId("issuer-id");
        config.setKeyId("key-id");
    }

    @Test
    void init_rejectsXcodeTestingWhenProdProfileIsActive() {
        config.setEnvironment("xcode-testing");
        when(environment.acceptsProfiles(Profiles.of("prod"))).thenReturn(true);

        AppleAppStoreBillingServiceImpl service =
                new AppleAppStoreBillingServiceImpl(config, new ObjectMapper(), environment);

        assertThrows(IllegalStateException.class, service::init);
    }

    @Test
    void init_requiresPrivateKeyPathWhenProdProfileIsActive() {
        config.setEnvironment("production");
        config.setPrivateKeyPath("");
        when(environment.acceptsProfiles(Profiles.of("prod"))).thenReturn(true);

        AppleAppStoreBillingServiceImpl service =
                new AppleAppStoreBillingServiceImpl(config, new ObjectMapper(), environment);

        assertThrows(IllegalStateException.class, service::init);
    }

    @Test
    void init_allowsXcodeTestingOutsideProdProfile() {
        config.setEnvironment("xcode-testing");
        when(environment.acceptsProfiles(Profiles.of("prod"))).thenReturn(false);

        AppleAppStoreBillingServiceImpl service =
                new AppleAppStoreBillingServiceImpl(config, new ObjectMapper(), environment);

        assertDoesNotThrow(service::init);
    }

    @Test
    void init_skipsAppleCredentialsWhenSubscriptionsAreDisabled() throws Exception {
        config.setEnvironment("production");
        config.setPrivateKeyPath("");

        AppleAppStoreBillingServiceImpl service =
                new AppleAppStoreBillingServiceImpl(config, new ObjectMapper(), environment);
        setSubscriptionsEnabled(service, false);

        assertDoesNotThrow(service::init);
    }

    private static void setSubscriptionsEnabled(AppleAppStoreBillingServiceImpl service, boolean enabled)
            throws Exception {
        Field field = AppleAppStoreBillingServiceImpl.class.getDeclaredField("subscriptionsEnabled");
        field.setAccessible(true);
        field.setBoolean(service, enabled);
    }
}
