package com.app.categorise.application.internal;

import com.app.categorise.data.entity.DeviceEntity;
import com.app.categorise.data.repository.DeviceRepository;
import com.google.api.core.ApiFutures;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
        @DisplayName("sends a VISIBLE notification with title/body and TRANSCRIPT_COMPLETE data")
        void sendsVisibleNotificationWithData() throws Exception {
            when(deviceRepository.findByUserIdAndActiveTrue(userId))
                    .thenReturn(List.of(iosDevice));

            try (MockedStatic<FirebaseApp> firebaseAppMock = mockStatic(FirebaseApp.class);
                 MockedStatic<FirebaseMessaging> messagingMock = mockStatic(FirebaseMessaging.class)) {

                firebaseAppMock.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));

                FirebaseMessaging messaging = mock(FirebaseMessaging.class);
                messagingMock.when(FirebaseMessaging::getInstance).thenReturn(messaging);
                ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
                when(messaging.sendAsync(messageCaptor.capture()))
                        .thenReturn(ApiFutures.immediateFuture("projects/p/messages/1"));

                notificationService.notifyJobCompleted(userId, jobId, transcriptId, "My Great Video");

                Message sent = messageCaptor.getValue();

                Map<String, String> notification = messageNotification(sent);
                assertThat(notification).isNotNull();
                assertThat(notification.get("title")).isEqualTo("Transcript ready");
                assertThat(notification.get("body")).isEqualTo("\"My Great Video\" is ready to view");

                Map<String, String> data = messageData(sent);
                assertThat(data).containsEntry("type", "TRANSCRIPT_COMPLETE");
                assertThat(data).containsEntry("jobId", jobId.toString());
                assertThat(data).containsEntry("transcriptId", transcriptId.toString());
            }
        }

        @Test
        @DisplayName("uses 'your video' body fallback when the title is blank")
        void usesFallbackTitleWhenBlank() throws Exception {
            when(deviceRepository.findByUserIdAndActiveTrue(userId))
                    .thenReturn(List.of(iosDevice));

            try (MockedStatic<FirebaseApp> firebaseAppMock = mockStatic(FirebaseApp.class);
                 MockedStatic<FirebaseMessaging> messagingMock = mockStatic(FirebaseMessaging.class)) {

                firebaseAppMock.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));
                FirebaseMessaging messaging = mock(FirebaseMessaging.class);
                messagingMock.when(FirebaseMessaging::getInstance).thenReturn(messaging);
                ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
                when(messaging.sendAsync(messageCaptor.capture()))
                        .thenReturn(ApiFutures.immediateFuture("ok"));

                notificationService.notifyJobCompleted(userId, jobId, transcriptId, "   ");

                Map<String, String> notification = messageNotification(messageCaptor.getValue());
                assertThat(notification.get("body")).isEqualTo("\"your video\" is ready to view");
            }
        }

        @Test
        @DisplayName("sends a SINGLE push per device (not a dual silent+visible pair)")
        void sendsSinglePushPerDevice() {
            when(deviceRepository.findByUserIdAndActiveTrue(userId))
                    .thenReturn(List.of(iosDevice));

            try (MockedStatic<FirebaseApp> firebaseAppMock = mockStatic(FirebaseApp.class);
                 MockedStatic<FirebaseMessaging> messagingMock = mockStatic(FirebaseMessaging.class)) {

                firebaseAppMock.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));
                FirebaseMessaging messaging = mock(FirebaseMessaging.class);
                messagingMock.when(FirebaseMessaging::getInstance).thenReturn(messaging);
                when(messaging.sendAsync(any(Message.class)))
                        .thenReturn(ApiFutures.immediateFuture("ok"));

                notificationService.notifyJobCompleted(userId, jobId, transcriptId, "Title");

                verify(messaging, times(1)).sendAsync(any(Message.class));
            }
        }

        @Test
        @DisplayName("does not send when no active devices exist")
        void doesNotSendWithoutActiveDevices() {
            when(deviceRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of());

            try (MockedStatic<FirebaseApp> firebaseAppMock = mockStatic(FirebaseApp.class);
                 MockedStatic<FirebaseMessaging> messagingMock = mockStatic(FirebaseMessaging.class)) {

                firebaseAppMock.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));
                FirebaseMessaging messaging = mock(FirebaseMessaging.class);
                messagingMock.when(FirebaseMessaging::getInstance).thenReturn(messaging);

                notificationService.notifyJobCompleted(userId, jobId, transcriptId, "Title");

                verify(deviceRepository).findByUserIdAndActiveTrue(userId);
                verify(messaging, never()).sendAsync(any(Message.class));
            }
        }

        @Test
        @DisplayName("skips entirely when Firebase is not initialized")
        void skipsWhenFirebaseNotInitialized() {
            try (MockedStatic<FirebaseApp> firebaseAppMock = mockStatic(FirebaseApp.class)) {
                firebaseAppMock.when(FirebaseApp::getApps).thenReturn(List.of());

                notificationService.notifyJobCompleted(userId, jobId, transcriptId, "Title");

                verify(deviceRepository, never()).findByUserIdAndActiveTrue(any());
            }
        }
    }

    @Nested
    @DisplayName("notifyJobFailed")
    class NotifyJobFailedTests {

        @Test
        @DisplayName("queries devices immediately for failures (no batching)")
        void queriesDevicesImmediatelyForFailures() {
            when(deviceRepository.findByUserIdAndActiveTrue(userId))
                    .thenReturn(List.of(iosDevice));

            try (MockedStatic<FirebaseApp> firebaseAppMock = mockStatic(FirebaseApp.class)) {
                firebaseAppMock.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));

                notificationService.notifyJobFailed(userId, jobId, "File format not supported");

                verify(deviceRepository).findByUserIdAndActiveTrue(userId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> messageData(Message message) throws Exception {
        Field f = Message.class.getDeclaredField("data");
        f.setAccessible(true);
        return (Map<String, String>) f.get(message);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> messageNotification(Message message) throws Exception {
        Field nf = Message.class.getDeclaredField("notification");
        nf.setAccessible(true);
        Object notification = nf.get(message);
        if (notification == null) return null;
        Field titleField = notification.getClass().getDeclaredField("title");
        Field bodyField = notification.getClass().getDeclaredField("body");
        titleField.setAccessible(true);
        bodyField.setAccessible(true);
        return Map.of(
                "title", String.valueOf(titleField.get(notification)),
                "body", String.valueOf(bodyField.get(notification))
        );
    }
}
