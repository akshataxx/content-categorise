package com.app.categorise.domain.service;

import com.app.categorise.api.dto.subscription.UsageInfoDto;

import java.util.UUID;

/**
 * Domain service interface for user usage information
 */
public interface UsageService {

    /**
     * Get usage information for a user
     *
     * @param userId the user ID
     * @return usage information including premium status and remaining transcriptions
     */
    UsageInfoDto getUserUsageInfo(UUID userId);
}
