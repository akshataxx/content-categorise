package com.app.categorise.application.internal;

import com.app.categorise.data.entity.DeviceEntity;
import com.app.categorise.data.repository.DeviceRepository;
import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for NotificationServiceImpl focusing on silent notification behavior.
 * Story 1 & 2: Backend - Silent Notification Infrastructure (Simplified)
 * 
 * These tests verify the core logic: device lookup, Firebase initialization checks,
 * and immediate notification sending. The actual Firebase message sending is tested via integration tests.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private DeviceRepository deviceRepository;

    private NotificationServiceImpl notificationService;

    private UUID userId;
    private UUID jobId;
    private UUID transcriptId;
    private DeviceEntity iosDevice;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(deviceRepository);

        userId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        transcriptId = UUID.randomUUID();

        iosDevice = new DeviceEntity();
        iosDevice.setId(UUID.randomUUID());
        iosDevice.setUserId(userId);
        iosDevice.setPlatform("IOS");
        iosDevice.setFcmToken("test-fcm-token-ios");
        iosDevice.setDeviceId("test-device-id");
        iosDevice.setActive(true);
        iosDevice.setCreatedAt(Instant.now());
        iosDevice.setUpdatedAt(Instant.now());
    }

    @Nested
    @DisplayName("notifyJobCompleted")
    class NotifyJobCompletedTests {

        @Test
        @DisplayName("Should send silent notification immediately when Firebase is initialized")
        void shouldSendSilentNotificationImmediately() {
            // Given
            when(deviceRepository.findByUserIdAndActiveTrue(userId))
                    .thenReturn(List.of(iosDevice));

            try (MockedStatic<FirebaseApp> firebaseAppMock = mockStatic(FirebaseApp.class)) {
                firebaseAppMock.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));

                // When
                notificationService.notifyJobCompleted(userId, jobId, transcriptId, "Test Video");

                // Then - Should query for devices immediately (no batching delay)
                verify(deviceRepository).findByUserIdAndActiveTrue(userId);
            }
        }

        @Test
        @DisplayName("Should handle gracefully when no active devices exist")
        void shouldHandleNoActiveDevices() {
            // Given
            when(deviceRepository.findByUserIdAndActiveTrue(userId))
                    .thenReturn(List.of());

            try (MockedStatic<FirebaseApp> firebaseAppMock = mockStatic(FirebaseApp.class)) {
                firebaseAppMock.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));

                // When
                notificationService.notifyJobCompleted(userId, jobId, transcriptId, "Test Video");

                // Then - Should query devices immediately but skip sending
                verify(deviceRepository).findByUserIdAndActiveTrue(userId);
            }
        }

        @Test
        @DisplayName("Should skip notification when Firebase not initialized")
        void shouldSkipWhenFirebaseNotInitialized() {
            // Given
            try (MockedStatic<FirebaseApp> firebaseAppMock = mockStatic(FirebaseApp.class)) {
                firebaseAppMock.when(FirebaseApp::getApps).thenReturn(List.of());

                // When
                notificationService.notifyJobCompleted(userId, jobId, transcriptId, "Test Video");

                // Then - Should not query devices if Firebase not initialized
                verify(deviceRepository, never()).findByUserIdAndActiveTrue(any());
            }
        }
    }

    @Nested
    @DisplayName("notifyJobFailed")
    class NotifyJobFailedTests {

        @Test
        @DisplayName("Should query devices immediately for failures (no batching)")
        void shouldQueryDevicesImmediatelyForFailures() {
            // Given
            when(deviceRepository.findByUserIdAndActiveTrue(userId))
                    .thenReturn(List.of(iosDevice));

            try (MockedStatic<FirebaseApp> firebaseAppMock = mockStatic(FirebaseApp.class)) {
                firebaseAppMock.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));

                // When
                notificationService.notifyJobFailed(userId, jobId, "File format not supported");

                // Then - Failures are sent immediately, no batching delay
                verify(deviceRepository).findByUserIdAndActiveTrue(userId);
            }
        }
    }
}
