package com.app.categorise.controller;

import com.app.categorise.data.dto.TikTokMetadata;
import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.application.internal.ProcessedVideoFiles;
import com.app.categorise.domain.service.VideoService;
import com.app.categorise.api.controller.VideoController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoControllerTest {

    @Mock
    private VideoService videoService;

    @InjectMocks
    private VideoController videoController;

    private final String testVideoUrl = "https://example.com/video";
    private final String testUserId = "testUserId";
    private final String testTranscript = "This is a test transcript";
    private TikTokMetadata testMetadata;
    private TranscriptDtoWithAliases testTranscriptDtoWithAlias;

    @BeforeEach
    void setUp() {
        testMetadata = new TikTokMetadata();
        testMetadata.setTitle("Test Video");
        testMetadata.setDescription("Test Description");
        testMetadata.setDuration(120);
        testMetadata.setUploadedEpoch(Instant.now().getEpochSecond());
        testMetadata.setAccountId("testAccountId");
        testMetadata.setAccount("testAccount");
        testMetadata.setIdentifierId("testChannelId");
        testMetadata.setIdentifier("testChannel");

        testTranscriptDtoWithAlias = new TranscriptDtoWithAliases(
                "testId",
                testVideoUrl,
                testTranscript,
                testMetadata.getDescription(),
                testMetadata.getTitle(),
            30.0,
                Instant.now(),
                testMetadata.getAccountId(),
                testMetadata.getAccount(),
                testMetadata.getIdentifierId(),
                testMetadata.getIdentifier(),
                "recipe",
                "testCategory",
                null
        );
    }

    @Test
    void handleVideo_WithValidUrl_ReturnsTranscript() throws Exception {
        // Arrange
        ProcessedVideoFiles mockFiles = mock(ProcessedVideoFiles.class);
        when(mockFiles.getAudioFile()).thenReturn(mock(File.class));
        when(mockFiles.getMetadataFile()).thenReturn(mock(File.class));
        
        when(videoService.extractAudioAndMetadata(testVideoUrl)).thenReturn(mockFiles);
        when(videoService.transcribeAudio(any(File.class))).thenReturn(testTranscript);
        when(videoService.extractMetadata(any(File.class))).thenReturn(testMetadata);
        when(videoService.processVideoAndCreateTranscript(eq(testVideoUrl), eq(testTranscript), eq(testMetadata), eq(testUserId)))
                .thenReturn(testTranscriptDtoWithAlias);

        // Act
        ResponseEntity<TranscriptDtoWithAliases> response = videoController.handleVideo(Map.of("videoUrl", testVideoUrl,
                "userId", testUserId));

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testTranscriptDtoWithAlias.getId(), response.getBody().getId());
        assertEquals(testVideoUrl, response.getBody().getVideoUrl());
        
        verify(videoService).extractAudioAndMetadata(testVideoUrl);
        verify(videoService).transcribeAudio(any(File.class));
        verify(videoService).extractMetadata(any(File.class));
        verify(videoService).processVideoAndCreateTranscript(eq(testVideoUrl), eq(testTranscript), eq(testMetadata),
                eq(testUserId));
        verify(mockFiles).close();
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
        when(videoService.extractAudioAndMetadata(testVideoUrl))
            .thenThrow(new RuntimeException("Test exception"));

        // Act & Assert
        assertThrows(
            RuntimeException.class,
            () -> videoController.handleVideo(Map.of("videoUrl", testVideoUrl, "userId", testUserId))
        );
        
        verify(videoService).extractAudioAndMetadata(testVideoUrl);
        verifyNoMoreInteractions(videoService);
    }
}
