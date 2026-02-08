package com.app.categorise.api.controller;

import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.domain.model.RateLimitResult;
import com.app.categorise.domain.service.RateLimitService;
import com.app.categorise.domain.service.UntranscribedLinkService;
import com.app.categorise.domain.service.VideoService;
import com.app.categorise.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VideoController.class)
class VideoControllerRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VideoService videoService;

    @MockBean
    private UntranscribedLinkService untranscribedLinkService;

    @MockBean
    private RateLimitService rateLimitService;

    private UUID userId;
    private UserPrincipal userPrincipal;
    private String videoUrl;
    private Map<String, String> requestBody;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userPrincipal = new UserPrincipal(userId, "Test User", "test@example.com", "test@example.com", null, java.util.Collections.emptyList());
        videoUrl = "https://www.youtube.com/watch?v=test123";
        requestBody = Map.of("videoUrl", videoUrl);
    }

    @Nested
    @DisplayName("POST /api/video/transcribe - Rate Limiting")
    class TranscribeRateLimitTests {

        @Test
        @DisplayName("Should allow transcription when rate limits are not exceeded")
        void shouldAllowTranscriptionWhenRateLimitsNotExceeded() throws Exception {
            // Given
            RateLimitResult allowedResult = RateLimitResult.allowed(
                    4, 
                    Instant.now().plus(1, ChronoUnit.MINUTES),
                    RateLimitResult.RateLimitType.PER_MINUTE
            );
            when(rateLimitService.checkRateLimit(userId)).thenReturn(allowedResult);
            
            TranscriptDtoWithAliases mockResponse = new TranscriptDtoWithAliases(
                    UUID.randomUUID(), videoUrl, "Test transcript content", null, "Test description", "Test title",
                    120.0, Instant.now(), "test-account-id", "Test Account", "test-identifier-id",
                    "Test Identifier", "Test Alias", UUID.randomUUID(), "Test Category", Instant.now(),
                    null // notes
            );
            when(videoService.processVideoAndCreateTranscript(eq(videoUrl), eq(userId)))
                    .thenReturn(CompletableFuture.completedFuture(mockResponse));

            // When & Then
            mockMvc.perform(post("/api/video/transcribe")
                    .with(user(userPrincipal))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            verify(rateLimitService).checkRateLimit(userId);
            verify(videoService).processVideoAndCreateTranscript(videoUrl, userId);
            verify(rateLimitService).recordTranscription(userId);
        }

        @Test
        @DisplayName("Should return 429 when per-minute rate limit exceeded")
        void shouldReturn429WhenPerMinuteRateLimitExceeded() throws Exception {
            // Given
            Instant resetTime = Instant.now().plus(1, ChronoUnit.MINUTES);
            RateLimitResult deniedResult = RateLimitResult.denied(
                    "Per-minute transcript limit exceeded (5/5)",
                    RateLimitResult.RateLimitType.PER_MINUTE,
                    resetTime
            );
            when(rateLimitService.checkRateLimit(userId)).thenReturn(deniedResult);

            // When & Then
            mockMvc.perform(post("/api/video/transcribe")
                    .with(user(userPrincipal))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string("X-RateLimit-Remaining", "0"))
                    .andExpect(header().string("X-RateLimit-Limit-Type", "PER_MINUTE"))
                    .andExpect(header().string("X-RateLimit-Reset", String.valueOf(resetTime.getEpochSecond())))
                    .andExpect(jsonPath("$.status").value(429))
                    .andExpect(jsonPath("$.error").value("Rate Limit Exceeded"))
                    .andExpect(jsonPath("$.message").value("Per-minute transcript limit exceeded (5/5)"));

            verify(rateLimitService).checkRateLimit(userId);
            verify(videoService, never()).processVideoAndCreateTranscript(anyString(), any(UUID.class));
            verify(rateLimitService, never()).recordTranscription(userId);
        }
    }
}