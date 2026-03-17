package com.app.categorise.api.controller;

import com.app.categorise.api.dto.DeviceRegistrationResponse;
import com.app.categorise.api.request.DeviceRegistrationRequest;
import com.app.categorise.data.entity.DeviceEntity;
import com.app.categorise.data.repository.DeviceRepository;
import com.app.categorise.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@Tag(name = "Device", description = "Device token registration for push notifications")
@RequestMapping("/api/device")
public class DeviceController {

    private static final Logger log = LoggerFactory.getLogger(DeviceController.class);

    private final DeviceRepository deviceRepository;

    public DeviceController(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Operation(summary = "Register device for push notifications")
    @PostMapping("/register")
    public ResponseEntity<DeviceRegistrationResponse> register(
            @RequestBody @Valid DeviceRegistrationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("User not authenticated");
        }
        UUID userId = principal.getId();

        // Check if device exists by fcmToken (token refresh / same device re-register)
        Optional<DeviceEntity> byToken = deviceRepository.findByFcmToken(request.fcmToken());
        if (byToken.isPresent()) {
            DeviceEntity existing = byToken.get();
            existing.setDeviceId(request.deviceId());
            deviceRepository.save(existing);
            log.info("Updated existing device {} for user {} (token refresh)", existing.getId(), userId);
            return ResponseEntity.ok(new DeviceRegistrationResponse(existing.getId(), false));
        }

        // Check if device exists by userId + deviceId (token rotation)
        Optional<DeviceEntity> byUserAndDevice = deviceRepository.findByUserIdAndDeviceId(userId, request.deviceId());
        if (byUserAndDevice.isPresent()) {
            DeviceEntity existing = byUserAndDevice.get();
            existing.setFcmToken(request.fcmToken());
            deviceRepository.save(existing);
            log.info("Updated token for device {} for user {} (token rotation)", existing.getId(), userId);
            return ResponseEntity.ok(new DeviceRegistrationResponse(existing.getId(), false));
        }

        // New registration
        DeviceEntity device = new DeviceEntity();
        device.setUserId(userId);
        device.setPlatform(request.platform());
        device.setFcmToken(request.fcmToken());
        device.setDeviceId(request.deviceId());
        device.setActive(true);
        device = deviceRepository.save(device);
        log.info("Registered new device {} for user {}", device.getId(), userId);
        return ResponseEntity.ok(new DeviceRegistrationResponse(device.getId(), true));
    }

    @Operation(summary = "Unregister device from push notifications")
    @DeleteMapping("/unregister")
    public ResponseEntity<Void> unregister(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String fcmToken,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("User not authenticated");
        }
        if ((deviceId == null || deviceId.isBlank()) && (fcmToken == null || fcmToken.isBlank())) {
            throw new IllegalArgumentException("Either deviceId or fcmToken must be provided");
        }
        UUID userId = principal.getId();

        if (deviceId != null && !deviceId.isBlank()) {
            deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                    .ifPresent(device -> {
                        deviceRepository.delete(device);
                        log.info("Unregistered device {} for user {}", device.getId(), userId);
                    });
        } else {
            deviceRepository.findByFcmToken(fcmToken)
                    .filter(d -> d.getUserId().equals(userId))
                    .ifPresent(device -> {
                        deviceRepository.delete(device);
                        log.info("Unregistered device {} for user {}", device.getId(), userId);
                    });
        }
        return ResponseEntity.noContent().build();
    }
}
