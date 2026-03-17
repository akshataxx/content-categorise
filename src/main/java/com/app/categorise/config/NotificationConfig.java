package com.app.categorise.config;

import com.app.categorise.application.internal.NoOpNotificationService;
import com.app.categorise.application.internal.NotificationServiceImpl;
import com.app.categorise.data.repository.DeviceRepository;
import com.app.categorise.domain.service.NotificationService;
import com.google.firebase.FirebaseApp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the NotificationService bean based on Firebase availability.
 *
 * When FirebaseApp is present (firebase.enabled=true), the real FCM implementation is used.
 * Otherwise, a no-op fallback is registered so the app can run without Firebase (dev/test).
 */
@Configuration
public class NotificationConfig {

    @Bean
    @ConditionalOnBean(FirebaseApp.class)
    public NotificationService notificationService(DeviceRepository deviceRepository) {
        return new NotificationServiceImpl(deviceRepository);
    }

    @Bean
    @ConditionalOnMissingBean(NotificationService.class)
    public NotificationService noOpNotificationService() {
        return new NoOpNotificationService();
    }
}
