package com.app.categorise.application.internal;

import com.app.categorise.application.mapper.SubscriptionMapper;
import com.app.categorise.data.entity.UserSubscriptionEntity;
import com.app.categorise.data.repository.UserSubscriptionRepository;
import com.app.categorise.data.repository.UserTranscriptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock private UserSubscriptionRepository subscriptionRepository;
    @Mock private UserTranscriptRepository transcriptRepository;
    @Mock private SubscriptionMapper mapper;

    private SubscriptionServiceImpl subscriptionService;

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionServiceImpl(
                subscriptionRepository,
                transcriptRepository,
                mapper
        );
    }

    @Test
    void upgradeToPremiumWithAppStore_rejectsOriginalTransactionOwnedByAnotherUser() {
        UUID attackerUserId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UserSubscriptionEntity ownerSubscription = new UserSubscriptionEntity(
                ownerUserId,
                UserSubscriptionEntity.SubscriptionType.PREMIUM_MONTHLY,
                UserSubscriptionEntity.SubscriptionStatus.ACTIVE
        );
        ownerSubscription.setAppleOriginalTransactionId("orig-tx-1");

        when(subscriptionRepository.findByAppleOriginalTransactionId("orig-tx-1"))
                .thenReturn(Optional.of(ownerSubscription));

        assertThrows(IllegalStateException.class, () ->
                subscriptionService.upgradeToPremiumWithAppStore(
                        attackerUserId,
                        "orig-tx-1",
                        "tx-2",
                        "premium_monthly",
                        Instant.now().plusSeconds(3600)
                ));

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void upgradeToPremiumWithAppStore_allowsOriginalTransactionAlreadyOwnedBySameUser() {
        UUID userId = UUID.randomUUID();
        UserSubscriptionEntity existing = new UserSubscriptionEntity(
                userId,
                UserSubscriptionEntity.SubscriptionType.PREMIUM_MONTHLY,
                UserSubscriptionEntity.SubscriptionStatus.ACTIVE
        );
        existing.setAppleOriginalTransactionId("orig-tx-1");

        when(subscriptionRepository.findByAppleOriginalTransactionId("orig-tx-1"))
                .thenReturn(Optional.of(existing));
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(existing)).thenReturn(existing);

        assertDoesNotThrow(() ->
                subscriptionService.upgradeToPremiumWithAppStore(
                        userId,
                        "orig-tx-1",
                        "tx-2",
                        "premium_monthly",
                        Instant.now().plusSeconds(3600)
                ));

        verify(subscriptionRepository).save(existing);
    }
}
