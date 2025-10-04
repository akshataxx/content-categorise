package com.app.categorise.domain.service;

import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.application.mapper.VideoMapper;
import com.app.categorise.data.client.whisper.WhisperClient;
import com.app.categorise.data.dto.TikTokMetadata;
import com.app.categorise.data.dto.TranscriptCategorisationResult;
import com.app.categorise.data.entity.BaseTranscriptEntity;
import com.app.categorise.data.entity.CategoryEntity;
import com.app.categorise.data.entity.UserTranscriptEntity;
import com.app.categorise.data.repository.BaseTranscriptRepository;
import com.app.categorise.data.repository.UserTranscriptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.app.categorise.util.processExecutor.ProcessExecutor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

    @Mock
    private WhisperClient whisperClient;

    @Mock
    private CategorisationService categorisationService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private CategoryAliasService categoryAliasService;

    @Mock
    private VideoMapper videoMapper;

    @Mock
    private BaseTranscriptRepository baseTranscriptRepository;

    @Mock
    private UserTranscriptRepository userTranscriptRepository;

    @Mock
    private ProcessExecutor processExecutor;

    private VideoService videoService;

    private UUID userId;
    private UUID baseTranscriptId;
    private UUID categoryId;
    private String videoUrl;
    private String transcriptText;
    private BaseTranscriptEntity baseTranscript;
    
    @Mock
    private UserTranscriptEntity userTranscript;
    
    private CategoryEntity category;
    private TranscriptDtoWithAliases expectedResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        // construct service with mocks and a mocked ProcessExecutor
        videoService = new VideoService(
                whisperClient,
                processExecutor,
                categorisationService,
                categoryService,
                categoryAliasService,
                videoMapper,
                baseTranscriptRepository,
                userTranscriptRepository,
                "/usr/bin/ffmpeg"
        );

        baseTranscriptId = UUID.randomUUID();
        UUID userTranscriptId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        videoUrl = "https://example.com/video";
        transcriptText = "This is a test transcript";

        // Setup TikTokMetadata
        TikTokMetadata metadata = new TikTokMetadata();
        metadata.setTitle("Test Video");
        metadata.setDescription("Test Description");
        metadata.setDuration(30);
        metadata.setUploadedEpoch(System.currentTimeMillis() / 1000);
        metadata.setAccountId("testAccountId");
        metadata.setAccount("testAccount");
        metadata.setIdentifierId("testChannelId");
        metadata.setIdentifier("testChannel");

        // Setup entities
        baseTranscript = createBaseTranscriptEntity();
        category = createCategoryEntity();

        // Setup expected response
        expectedResponse = new TranscriptDtoWithAliases(
            userTranscriptId,
            videoUrl,
            transcriptText,
            "Test Description",
            "Test Video",
            30.0,
            Instant.now(),
            "testAccountId",
            "testAccount",
            "testChannelId",
            "testChannel",
            "recipe",
            categoryId,
            "testCategory",
            Instant.now()
        );
    }

    @Nested
    @DisplayName("Process Video and Create Transcript")
    class ProcessVideoAndCreateTranscriptTests {

        @Test
        @DisplayName("Should reuse existing base transcript and create new user transcript")
        void processVideoAndCreateTranscript_ExistingBaseTranscript_CreatesNewUserTranscript() throws Exception {
            // Arrange
            when(baseTranscriptRepository.findByVideoUrl(videoUrl)).thenReturn(Optional.of(baseTranscript));
            when(userTranscriptRepository.findByUserIdAndBaseTranscriptIdWithBaseTranscript(userId, baseTranscriptId))
                .thenReturn(Optional.empty());
            
            TranscriptCategorisationResult categorisationResult = new TranscriptCategorisationResult(
                "testCategory", "genericTopic", "recipe"
            );
            when(categorisationService.classifyAndSuggestAlias(anyString(), anyString(), anyString()))
                .thenReturn(categorisationResult);
            
            when(categoryService.saveIfNotExists("testCategory", "", userId)).thenReturn(category);
            when(categoryAliasService.findByUserIdAndCategoryId(userId, categoryId)).thenReturn(Optional.empty());
            when(videoMapper.createUserTranscriptEntity(userId, baseTranscript, category)).thenReturn(userTranscript);
            when(userTranscriptRepository.save(userTranscript)).thenReturn(userTranscript);
            when(videoMapper.buildResponse(baseTranscript, userTranscript, "testCategory", "recipe"))
                .thenReturn(expectedResponse);

            // Act
            TranscriptDtoWithAliases result = videoService.processVideoAndCreateTranscript(videoUrl, userId);

            // Assert
            assertNotNull(result);
            assertEquals(expectedResponse, result);
            verify(baseTranscriptRepository).findByVideoUrl(videoUrl);
            verify(userTranscriptRepository).findByUserIdAndBaseTranscriptIdWithBaseTranscript(userId, baseTranscriptId);
            verify(categorisationService).classifyAndSuggestAlias(transcriptText, "Test Video", "Test Description");
            verify(videoMapper).createUserTranscriptEntity(userId, baseTranscript, category);
            verify(userTranscriptRepository).save(userTranscript);
        }

        @Test
        @DisplayName("Should return existing user transcript when user already has access")
        void processVideoAndCreateTranscript_ExistingUserTranscript_UpdatesLastAccessed() throws Exception {
            // Arrange
            when(baseTranscriptRepository.findByVideoUrl(videoUrl)).thenReturn(Optional.of(baseTranscript));
            when(userTranscriptRepository.findByUserIdAndBaseTranscriptIdWithBaseTranscript(userId, baseTranscriptId))
                .thenReturn(Optional.of(userTranscript));
            
            when(userTranscriptRepository.save(userTranscript)).thenReturn(userTranscript);
            when(videoMapper.buildResponse(baseTranscript, userTranscript)).thenReturn(expectedResponse);

            // Act
            TranscriptDtoWithAliases result = videoService.processVideoAndCreateTranscript(videoUrl, userId);

            // Assert
            assertNotNull(result);
            assertEquals(expectedResponse, result);
            verify(baseTranscriptRepository).findByVideoUrl(videoUrl);
            verify(userTranscriptRepository).findByUserIdAndBaseTranscriptIdWithBaseTranscript(userId, baseTranscriptId);
            verify(userTranscript).setLastAccessedAt(any(Instant.class));
            verify(userTranscriptRepository).save(userTranscript);
            verify(videoMapper).buildResponse(baseTranscript, userTranscript);
             // media tools should not run when user already has access
             verify(processExecutor, never()).run(any(String[].class));
            
            // Should not create new transcript or categorize
            verify(categorisationService, never()).classifyAndSuggestAlias(anyString(), anyString(), anyString());
            verify(videoMapper, never()).createUserTranscriptEntity(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Extract Audio and Metadata and Process Execution")
    class ExtractAudioAndMetadataTests {

        @Test
        @DisplayName("Should extract audio and metadata successfully")
        void extractAudioAndMetadata_ValidUrl_ReturnsProcessedFiles() throws Exception {
            String testUrl = "https://example.com/video";
            // With a no-op ProcessExecutor, calling extractAudioAndMetadata should attempt to read files.
            // We only verify that the method can be invoked; deeper IO behaviour is covered in separate tests.
            assertDoesNotThrow(() -> videoService.extractAudioAndMetadata(testUrl));
            verify(processExecutor, atLeastOnce()).run(any(String[].class));
        }
    }

    @Nested
    @DisplayName("Transcribe Audio")
    class TranscribeAudioTests {

        @Test
        @DisplayName("Should transcribe audio using WhisperClient")
        void transcribeAudio_ValidFile_ReturnsTranscript() {
            // Arrange
            File audioFile = mock(File.class);
            String expectedTranscript = "This is the transcribed text";
            when(whisperClient.transcribeAudio(audioFile)).thenReturn(expectedTranscript);

            // Act
            String result = videoService.transcribeAudio(audioFile);

            // Assert
            assertEquals(expectedTranscript, result);
            verify(whisperClient).transcribeAudio(audioFile);
        }
    }

    // Helper methods
    private BaseTranscriptEntity createBaseTranscriptEntity() {
        BaseTranscriptEntity entity = new BaseTranscriptEntity();
        entity.setId(baseTranscriptId);
        entity.setVideoUrl(videoUrl);
        entity.setTranscript(transcriptText);
        entity.setTitle("Test Video");
        entity.setDescription("Test Description");
        entity.setDuration(30.0);
        entity.setUploadedAt(Instant.now());
        entity.setAccountId("testAccountId");
        entity.setAccount("testAccount");
        entity.setIdentifierId("testChannelId");
        entity.setIdentifier("testChannel");
        entity.setCreatedAt(Instant.now());
        return entity;
    }


    private CategoryEntity createCategoryEntity() {
        CategoryEntity entity = new CategoryEntity();
        entity.setId(categoryId);
        entity.setName("testCategory");
        entity.setDescription("");
        entity.setCreatedBy(userId);
        return entity;
    }
}