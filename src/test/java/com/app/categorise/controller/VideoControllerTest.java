package com.app.categorise.controller;

import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.domain.service.VideoService;
import com.app.categorise.domain.service.UntranscribedLinkService;
import com.app.categorise.api.controller.VideoController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoControllerTest {

    @Mock
    private VideoService videoService;

    @Mock
    private UntranscribedLinkService untranscribedLinkService;

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
                Instant.now()                // createdAt
        );
    }

    @Test
    void handleVideo_WithValidUrl_ReturnsTranscript() throws Exception {
        // Arrange
        when(videoService.processVideoAndCreateTranscript(eq(testVideoUrl), eq(testUserId)))
                .thenReturn(testTranscriptDtoWithAlias);

        // Act
        ResponseEntity<TranscriptDtoWithAliases> response = videoController.handleVideo(Map.of("videoUrl", testVideoUrl,
                "userId", testUserId.toString()));

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testTranscriptDtoWithAlias.id(), response.getBody().id());
        assertEquals(testVideoUrl, response.getBody().videoUrl());
        
        // Verify the new simplified method is called
        verify(videoService).processVideoAndCreateTranscript(eq(testVideoUrl), eq(testUserId));
        verify(untranscribedLinkService).deleteLink(testUserId, testVideoUrl);
    }

    @Test
    void handleVideo_WithMissingVideoUrl_ThrowsIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> videoController.handleVideo(Map.of("wrongKey", testVideoUrl))
        );
        
        assertEquals("Missing 'videoUrl' in request body", exception.getMessage());
        verifyNoInteractions(videoService);
    }

    @Test
    void handleVideo_WithEmptyVideoUrl_ThrowsIllegalArgumentException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> videoController.handleVideo(Map.of("videoUrl", ""))
        );
        
        assertEquals("Missing 'videoUrl' in request body", exception.getMessage());
        verifyNoInteractions(videoService);
    }

    @Test
    void handleVideo_WhenServiceThrowsException_PropagatesException() throws Exception {
        // Arrange
        when(videoService.processVideoAndCreateTranscript(testVideoUrl, testUserId))
            .thenThrow(new RuntimeException("Test exception"));

        // Act & Assert
        assertThrows(
            RuntimeException.class,
            () -> videoController.handleVideo(Map.of("videoUrl", testVideoUrl, "userId", testUserId.toString()))
        );
        
        verify(videoService).processVideoAndCreateTranscript(testVideoUrl, testUserId);
        verifyNoMoreInteractions(videoService);
    }
}
