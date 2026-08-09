package com.app.categorise.domain.service;

import com.app.categorise.data.repository.DeviceRepository;
import com.app.categorise.data.repository.RefreshTokenRepository;
import com.app.categorise.data.repository.TranscriptionJobRepository;
import com.app.categorise.data.repository.UntranscribedLinkRepository;
import com.app.categorise.data.repository.UserRateLimitRepository;
import com.app.categorise.data.repository.UserRateLimitTrackingRepository;
import com.app.categorise.data.repository.UserRepository;
import com.app.categorise.data.repository.UserSubcategoryRepository;
import com.app.categorise.data.repository.UserSubscriptionRepository;
import com.app.categorise.data.repository.UserTranscriptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserTranscriptRepository userTranscriptRepository;
    @Mock private UserSubcategoryRepository userSubcategoryRepository;
    @Mock private UserSubscriptionRepository userSubscriptionRepository;
    @Mock private TranscriptionJobRepository transcriptionJobRepository;
    @Mock private UntranscribedLinkRepository untranscribedLinkRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRateLimitRepository userRateLimitRepository;
    @Mock private UserRateLimitTrackingRepository userRateLimitTrackingRepository;

    private UserService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UserService(
                userRepository,
                userTranscriptRepository,
                userSubcategoryRepository,
                userSubscriptionRepository,
                transcriptionJobRepository,
                untranscribedLinkRepository,
                deviceRepository,
                refreshTokenRepository,
                userRateLimitRepository,
                userRateLimitTrackingRepository);
    }

    @Test
    void deleteAccount_removesAllUserScopedDataAndUser() {
        when(userRepository.existsById(userId)).thenReturn(true);

        service.deleteAccount(userId);

        verify(userTranscriptRepository).deleteByUserId(userId);
        verify(userSubcategoryRepository).deleteByUserId(userId);
        verify(userSubscriptionRepository).deleteByUserId(userId);
        verify(transcriptionJobRepository).deleteByUserId(userId);
        verify(untranscribedLinkRepository).deleteByUserId(userId);
        verify(deviceRepository).deleteByUserId(userId);
        verify(refreshTokenRepository).deleteByUserId(userId);
        verify(userRateLimitRepository).deleteByUserId(userId);
        verify(userRateLimitTrackingRepository).deleteByUserId(userId);
        verify(userRepository).deleteById(userId);
    }

    @Test
    void deleteAccount_throwsAndDeletesNothingWhenUserMissing() {
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThrows(UsernameNotFoundException.class, () -> service.deleteAccount(userId));

        verify(userTranscriptRepository, never()).deleteByUserId(userId);
        verify(userRepository, never()).deleteById(userId);
    }
}
