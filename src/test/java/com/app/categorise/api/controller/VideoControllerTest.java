package com.app.categorise.api.controller;

import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.domain.model.RateLimitResult;
import com.app.categorise.domain.service.RateLimitService;
import com.app.categorise.domain.service.VideoService;
import com.app.categorise.domain.service.UntranscribedLinkService;
import com.app.categorise.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoControllerTest {

    @Mock
    private VideoService videoService;

    @Mock
    private UntranscribedLinkService untranscribedLinkService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private UserPrincipal mockPrincipal;

    @InjectMocks
    private VideoController videoController;

    private final String testVideoUrl = "https://example.com/video";
    private final UUID testUserId = UUID.randomUUID();
    private TranscriptDtoWithAliases testTranscriptDtoWithAlias;

    @BeforeEach
    void setUp() {
        testTranscriptDtoWithAlias = new TranscriptDtoWithAliases(
                UUID.randomUUID(),           // id
                testVideoUrl,                // videoUrl
                "This is a test transcript", // transcript
                null,                        // structuredContent
                "Test Description",          // description
                "Test Video",                // title
                30.0,                        // duration
                Instant.now(),               // uploadedAt
                "testAccountId",             // accountId
                "testAccount",               // account
                "testChannelId",             // identifierId
                "testChannel",               // identifier
                "recipe",                    // alias
                UUID.randomUUID(),           // categoryId
                "testCategory",              // category
                Instant.now(),               // createdAt
                null                         // notes
        );
    }

    @Test
    void handleVideo_WithValidUrl_ReturnsTranscript() throws Exception {
        // Arrange
        when(mockPrincipal.getId()).thenReturn(testUserId);
        
        // Mock rate limiting to allow the request
        RateLimitResult allowedResult = RateLimitResult.allowed(4, Instant.now().plus(1, ChronoUnit.MINUTES), RateLimitResult.RateLimitType.PER_MINUTE);
        when(rateLimitService.checkRateLimit(testUserId)).thenReturn(allowedResult);
        
        when(videoService.processVideoAndCreateTranscript(eq(testVideoUrl), eq(testUserId)))
                .thenReturn(CompletableFuture.completedFuture(testTranscriptDtoWithAlias));

        // Act
        CompletableFuture<ResponseEntity<TranscriptDtoWithAliases>> responseFuture = videoController.handleVideo(
            Map.of("videoUrl", testVideoUrl), mockPrincipal);

        // Assert
        assertNotNull(responseFuture);
        ResponseEntity<TranscriptDtoWithAliases> response = responseFuture.get(2, TimeUnit.SECONDS);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testTranscriptDtoWithAlias.id(), response.getBody().id());
        assertEquals(testVideoUrl, response.getBody().videoUrl());
        
        // Verify the rate limit check and recording
        verify(rateLimitService).checkRateLimit(testUserId);
        verify(rateLimitService).recordTranscription(testUserId);

        // Verify the new simplified method is called
        verify(videoService).processVideoAndCreateTranscript(eq(testVideoUrl), eq(testUserId));
        verify(untranscribedLinkService).deleteLink(testUserId, testVideoUrl);
        verify(rateLimitService).recordTranscription(testUserId);
    }

    @Test
    void handleVideo_WithMissingVideoUrl_ThrowsIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> videoController.handleVideo(Map.of("wrongKey", testVideoUrl), mockPrincipal)
        );
        
        assertEquals("Missing 'videoUrl' in request body", exception.getMessage());
        verifyNoInteractions(videoService);
    }

    @Test
    void handleVideo_WithEmptyVideoUrl_ThrowsIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> videoController.handleVideo(Map.of("videoUrl", ""), mockPrincipal)
        );
        
        assertEquals("Missing 'videoUrl' in request body", exception.getMessage());
        verifyNoInteractions(videoService);
    }

    @Test
    void handleVideo_WithNullPrincipal_ThrowsIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> videoController.handleVideo(Map.of("videoUrl", testVideoUrl), null)
        );
        
        assertEquals("User not authenticated", exception.getMessage());
        verifyNoInteractions(videoService);
    }

    @Test
    void handleVideo_WhenServiceThrowsException_PropagatesException() throws Exception {
        // Arrange
        when(mockPrincipal.getId()).thenReturn(testUserId);
        
        // Mock rate limiting to allow the request
        RateLimitResult allowedResult = RateLimitResult.allowed(4, Instant.now().plus(1, java.time.temporal.ChronoUnit.MINUTES), RateLimitResult.RateLimitType.PER_MINUTE);
        when(rateLimitService.checkRateLimit(testUserId)).thenReturn(allowedResult);
        
        when(videoService.processVideoAndCreateTranscript(testVideoUrl, testUserId))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Test exception")));

        // Act & Assert
        assertThrows(
            RuntimeException.class,
            () -> videoController.handleVideo(Map.of("videoUrl", testVideoUrl), mockPrincipal).join()
        );
        
        verify(rateLimitService).checkRateLimit(testUserId);
        verify(videoService).processVideoAndCreateTranscript(testVideoUrl, testUserId);
        // Should not record transcription when service throws exception
        verify(rateLimitService, never()).recordTranscription(testUserId);
        verifyNoMoreInteractions(videoService);
    }
}
