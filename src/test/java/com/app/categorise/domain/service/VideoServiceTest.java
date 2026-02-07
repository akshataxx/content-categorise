package com.app.categorise.domain.service;

import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.application.mapper.VideoMapper;
import com.app.categorise.data.client.whisper.WhisperClient;
import com.app.categorise.data.dto.TranscriptCategorisationResult;
import com.app.categorise.data.entity.BaseTranscriptEntity;
import com.app.categorise.data.entity.CategoryEntity;
import com.app.categorise.data.entity.UserTranscriptEntity;
import com.app.categorise.data.repository.BaseTranscriptRepository;
import com.app.categorise.data.repository.UserTranscriptRepository;
import com.app.categorise.util.processExecutor.ProcessExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

    @Mock private WhisperClient whisperClient;
    @Mock private CategorisationService categorisationService;
    @Mock private CategoryService categoryService;
    @Mock private CategoryAliasService categoryAliasService;
    @Mock private VideoMapper videoMapper;
    @Mock private BaseTranscriptRepository baseTranscriptRepository;
    @Mock private UserTranscriptRepository userTranscriptRepository;
    @Mock private ProcessExecutor processExecutor;

    private VideoService videoService;

    private UUID userId;
    private UUID baseTranscriptId;
    private UUID categoryId;
    private String videoUrl;
    private String transcriptText;
    private BaseTranscriptEntity baseTranscript;
    @Mock private UserTranscriptEntity userTranscript;
    private CategoryEntity category;
    private TranscriptDtoWithAliases expectedResponse;

    @BeforeEach
    void setUp() {
        Executor direct = Runnable::run; // run async work on calling thread in tests
        videoService = new VideoService(
                "/usr/bin/ffmpeg",
                direct,
                baseTranscriptRepository,
                categoryAliasService,
                categorisationService,
                categoryService,
                processExecutor,
                userTranscriptRepository,
                videoMapper,
                whisperClient
        );

        baseTranscriptId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        transcriptText = "This is a test transcript";
        userId = UUID.randomUUID();
        videoUrl = "https://example.com/video";

        baseTranscript = createBaseTranscriptEntity();
        category = createCategoryEntity();

        UUID userTranscriptId = UUID.randomUUID();
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
                Instant.now(),
                null // notes
        );
    }

    @Nested
    @DisplayName("Process Video and Create Transcript (Async)")
    class ProcessVideoAndCreateTranscriptTests {

        @Test
        @DisplayName("Reuses existing base transcript and creates new user transcript")
        void reuseBaseTranscript_createsUserTranscript_async() throws Exception {
            when(baseTranscriptRepository.findByVideoUrl(videoUrl)).thenReturn(Optional.of(baseTranscript));
            when(userTranscriptRepository.findByUserIdAndBaseTranscriptIdWithBaseTranscript(userId, baseTranscriptId))
                    .thenReturn(Optional.empty());

            TranscriptCategorisationResult catRes = new TranscriptCategorisationResult("testCategory", "genericTopic", "recipe");
            when(categorisationService.classifyAndSuggestAlias(anyString(), anyString(), anyString()))
                    .thenReturn(catRes);

            when(categoryService.saveIfNotExists("testCategory", "", userId)).thenReturn(category);
            when(categoryAliasService.findByUserIdAndCategoryId(userId, categoryId)).thenReturn(Optional.empty());
            when(videoMapper.createUserTranscriptEntity(userId, baseTranscript, category)).thenReturn(userTranscript);
            when(userTranscriptRepository.save(userTranscript)).thenReturn(userTranscript);
            when(videoMapper.buildResponse(baseTranscript, userTranscript, "testCategory", "recipe"))
                    .thenReturn(expectedResponse);

            TranscriptDtoWithAliases result = videoService.processVideoAndCreateTranscript(videoUrl, userId)
                    .get(2, java.util.concurrent.TimeUnit.SECONDS);

            assertNotNull(result);
            assertEquals(expectedResponse, result);
            verify(baseTranscriptRepository).findByVideoUrl(videoUrl);
            verify(userTranscriptRepository).findByUserIdAndBaseTranscriptIdWithBaseTranscript(userId, baseTranscriptId);
            verify(categorisationService).classifyAndSuggestAlias(transcriptText, "Test Video", "Test Description");
            verify(videoMapper).createUserTranscriptEntity(userId, baseTranscript, category);
            verify(userTranscriptRepository).save(userTranscript);
        }

        @Test
        @DisplayName("Returns existing user transcript when user already has access")
        void existingUserTranscript_updatesLastAccessed_async() throws Exception {
            when(baseTranscriptRepository.findByVideoUrl(videoUrl)).thenReturn(Optional.of(baseTranscript));
            when(userTranscriptRepository.findByUserIdAndBaseTranscriptIdWithBaseTranscript(userId, baseTranscriptId))
                    .thenReturn(Optional.of(userTranscript));

            when(userTranscriptRepository.save(userTranscript)).thenReturn(userTranscript);
            when(videoMapper.buildResponse(baseTranscript, userTranscript)).thenReturn(expectedResponse);

            TranscriptDtoWithAliases result = videoService.processVideoAndCreateTranscript(videoUrl, userId)
                    .get(2, java.util.concurrent.TimeUnit.SECONDS);

            assertNotNull(result);
            assertEquals(expectedResponse, result);
            verify(baseTranscriptRepository).findByVideoUrl(videoUrl);
            verify(userTranscriptRepository).findByUserIdAndBaseTranscriptIdWithBaseTranscript(userId, baseTranscriptId);
            verify(userTranscript).setLastAccessedAt(any(Instant.class));
            verify(userTranscriptRepository).save(userTranscript);
            verify(videoMapper).buildResponse(baseTranscript, userTranscript);
            verify(processExecutor, never()).run(any(String[].class));

            verify(categorisationService, never()).classifyAndSuggestAlias(anyString(), anyString(), anyString());
            verify(videoMapper, never()).createUserTranscriptEntity(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Extract Audio and Metadata and Process Execution")
    class ExtractAudioAndMetadataTests {
        @Test
        @DisplayName("Extracts audio and metadata successfully")
        void extractAudioAndMetadata_invokesProcessExecutor() throws Exception {
            String testUrl = "https://example.com/video";
            assertDoesNotThrow(() -> videoService.extractAudioAndMetadata(testUrl));
            verify(processExecutor, atLeastOnce()).run(any(String[].class));
        }
    }

    @Nested
    @DisplayName("Transcribe Audio")
    class TranscribeAudioTests {
        @Test
        @DisplayName("Transcribes audio using WhisperClient")
        void transcribeAudio_ValidFile_ReturnsTranscript() {
            File audioFile = mock(File.class);
            String expectedTranscript = "This is the transcribed text";
            when(whisperClient.transcribeAudio(audioFile)).thenReturn(expectedTranscript);

            String result = videoService.transcribeAudio(audioFile);

            assertEquals(expectedTranscript, result);
            verify(whisperClient).transcribeAudio(audioFile);
        }
    }

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
