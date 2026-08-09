package com.app.categorise.application.internal;

import com.app.categorise.api.dto.subscription.UsageInfoDto;
import com.app.categorise.domain.service.SubscriptionService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class UsageServiceImplTest {

    @Test
    void grantsComplimentaryAccessWhenSubscriptionsAreDisabled() throws Exception {
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        UsageServiceImpl usageService = new UsageServiceImpl(subscriptionService);
        setSubscriptionsEnabled(usageService, false);

        UsageInfoDto usage = usageService.getUserUsageInfo(UUID.randomUUID());

        assertTrue(usage.isPremium());
        assertEquals(Integer.MAX_VALUE, usage.getRemainingFreeTranscriptions());
        assertEquals(Integer.MAX_VALUE, usage.getTotalFreeTranscriptions());
        verifyNoInteractions(subscriptionService);
    }

    private static void setSubscriptionsEnabled(UsageServiceImpl usageService, boolean enabled) throws Exception {
        Field field = UsageServiceImpl.class.getDeclaredField("subscriptionsEnabled");
        field.setAccessible(true);
        field.setBoolean(usageService, enabled);
    }
}
