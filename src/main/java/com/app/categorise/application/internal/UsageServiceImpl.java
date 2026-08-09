package com.app.categorise.application.internal;

import com.app.categorise.api.dto.subscription.UsageInfoDto;
import com.app.categorise.domain.service.SubscriptionService;
import com.app.categorise.domain.service.UsageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Implementation of UsageService for retrieving user usage information
 */
@Service
public class UsageServiceImpl implements UsageService {

    private final SubscriptionService subscriptionService;

    @Value("${app.subscriptions.enabled:false}")
    private boolean subscriptionsEnabled = true;

    public UsageServiceImpl(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Override
    public UsageInfoDto getUserUsageInfo(UUID userId) {
        if (!subscriptionsEnabled) {
            return new UsageInfoDto(true, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        boolean isPremium = subscriptionService.hasActivePremiumSubscription(userId);
        int remainingFree = subscriptionService.getRemainingFreeTranscriptions(userId);
        int totalFree = subscriptionService.getFreeTierLimit();

        return new UsageInfoDto(isPremium, remainingFree, totalFree);
    }
}
