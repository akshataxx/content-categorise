package com.app.categorise.domain.service;

import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.application.mapper.VideoMapper;
import com.app.categorise.data.client.whisper.WhisperClient;
import com.app.categorise.data.dto.VideoMetadata;
import com.app.categorise.data.dto.TranscriptCategorisationResult;
import com.app.categorise.data.client.openai.OpenAIClient;
import com.app.categorise.data.entity.BaseTranscriptEntity;
import com.app.categorise.data.entity.CategoryEntity;
import com.app.categorise.data.entity.UserTranscriptEntity;
import com.app.categorise.data.repository.BaseTranscriptRepository;
import com.app.categorise.data.repository.UserTranscriptRepository;
import com.app.categorise.exception.VideoProcessingException;
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
    @Mock private OpenAIClient openAIClient;

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
                4,
                direct,
                baseTranscriptRepository,
                categoryAliasService,
                categorisationService,
                categoryService,
                openAIClient,
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
                null, // structuredContent
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
                null, // notes
                null, // platform
                null  // generatedTitle
        );
    }

    @Nested
    @DisplayName("Process Video and Create Transcript (Async)")
    class ProcessVideoAndCreateTranscriptTests {

        @Test
        @DisplayName("Reuses existing base transcript and creates new user transcript")
        void reuseBaseTranscript_createsUserTranscript_async() throws Exception {
            when(baseTranscriptRepository.findByVideoUrl(videoUrl)).thenReturn(Optional.of(baseTranscript));
            when(userTranscriptRepository.findByUserIdAndBaseTranscript_Id(userId, baseTranscriptId))
                    .thenReturn(Optional.empty());

            TranscriptCategorisationResult catRes = new TranscriptCategorisationResult("testCategory", "genericTopic", "recipe", "Test Generated Title");
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
            verify(userTranscriptRepository).findByUserIdAndBaseTranscript_Id(userId, baseTranscriptId);
            verify(categorisationService).classifyAndSuggestAlias(transcriptText, "Test Video", "Test Description");
            verify(videoMapper).createUserTranscriptEntity(userId, baseTranscript, category);
            verify(userTranscriptRepository).save(userTranscript);
        }

        @Test
        @DisplayName("Returns existing user transcript when user already has access")
        void existingUserTranscript_updatesLastAccessed_async() throws Exception {
            when(baseTranscriptRepository.findByVideoUrl(videoUrl)).thenReturn(Optional.of(baseTranscript));
            when(userTranscriptRepository.findByUserIdAndBaseTranscript_Id(userId, baseTranscriptId))
                    .thenReturn(Optional.of(userTranscript));

            when(userTranscriptRepository.save(userTranscript)).thenReturn(userTranscript);
            when(videoMapper.buildResponse(baseTranscript, userTranscript)).thenReturn(expectedResponse);

            TranscriptDtoWithAliases result = videoService.processVideoAndCreateTranscript(videoUrl, userId)
                    .get(2, java.util.concurrent.TimeUnit.SECONDS);

            assertNotNull(result);
            assertEquals(expectedResponse, result);
            verify(baseTranscriptRepository).findByVideoUrl(videoUrl);
            verify(userTranscriptRepository).findByUserIdAndBaseTranscript_Id(userId, baseTranscriptId);
            verify(userTranscript).setLastAccessedAt(any(Instant.class));
            verify(userTranscriptRepository).save(userTranscript);
            verify(videoMapper).buildResponse(baseTranscript, userTranscript);
            verify(processExecutor, never()).run(anyInt(), any(String[].class));

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
            String testUrl = "https://www.youtube.com/watch?v=abc123";
            assertDoesNotThrow(() -> videoService.extractAudioAndMetadata(testUrl));
            verify(processExecutor, atLeastOnce()).run(anyInt(), any(String[].class));
        }

        @Test
        @DisplayName("Includes -- separator before videoUrl to prevent argument injection")
        void extractAudioAndMetadata_includesArgSeparatorBeforeUrl() throws Exception {
            String testUrl = "https://www.youtube.com/watch?v=abc123";
            videoService.extractAudioAndMetadata(testUrl);

            org.mockito.ArgumentCaptor<String[]> captor = org.mockito.ArgumentCaptor.forClass(String[].class);
            verify(processExecutor, atLeastOnce()).run(anyInt(), captor.capture());

            String[] command = captor.getValue();
            // The last two elements should be "--" followed by the URL
            assertEquals("--", command[command.length - 2], "Second-to-last arg should be '--' separator");
            assertEquals(testUrl, command[command.length - 1], "Last arg should be the video URL");
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

    @Nested
    @DisplayName("Validate Transcript Data")
    class ValidateTranscriptDataTests {

        private VideoMetadata validMetadata;

        @BeforeEach
        void setUpMetadata() {
            validMetadata = new VideoMetadata();
            validMetadata.setTitle("My Video Title");
            validMetadata.setDuration(60);
            validMetadata.setUploadedEpoch(1700000000L);
        }

        @Test
        @DisplayName("Does not throw when all data is valid")
        void validate_allValid_doesNotThrow() {
            assertDoesNotThrow(() ->
                videoService.validateTranscriptData("some transcript", validMetadata, "https://example.com/video")
            );
        }

        @Test
        @DisplayName("Throws VideoProcessingException when transcript text is null")
        void validate_nullTranscript_throwsVideoProcessingException() {
            VideoProcessingException ex = assertThrows(VideoProcessingException.class, () ->
                videoService.validateTranscriptData(null, validMetadata, "https://example.com/video")
            );
            assertTrue(ex.getMessage().contains("transcript text is empty"));
        }

        @Test
        @DisplayName("Throws VideoProcessingException when transcript text is blank")
        void validate_blankTranscript_throwsVideoProcessingException() {
            VideoProcessingException ex = assertThrows(VideoProcessingException.class, () ->
                videoService.validateTranscriptData("   ", validMetadata, "https://example.com/video")
            );
            assertTrue(ex.getMessage().contains("transcript text is empty"));
        }

        @Test
        @DisplayName("Throws VideoProcessingException when title is missing")
        void validate_missingTitle_throwsVideoProcessingException() {
            validMetadata.setTitle(null);
            VideoProcessingException ex = assertThrows(VideoProcessingException.class, () ->
                videoService.validateTranscriptData("some transcript", validMetadata, "https://example.com/video")
            );
            assertTrue(ex.getMessage().contains("title is missing"));
        }

        @Test
        @DisplayName("Throws VideoProcessingException when duration is zero")
        void validate_zeroDuration_throwsVideoProcessingException() {
            validMetadata.setDuration(0);
            VideoProcessingException ex = assertThrows(VideoProcessingException.class, () ->
                videoService.validateTranscriptData("some transcript", validMetadata, "https://example.com/video")
            );
            assertTrue(ex.getMessage().contains("duration is invalid"));
        }

        @Test
        @DisplayName("Throws VideoProcessingException when duration is negative")
        void validate_negativeDuration_throwsVideoProcessingException() {
            validMetadata.setDuration(-5);
            VideoProcessingException ex = assertThrows(VideoProcessingException.class, () ->
                videoService.validateTranscriptData("some transcript", validMetadata, "https://example.com/video")
            );
            assertTrue(ex.getMessage().contains("duration is invalid"));
        }

        @Test
        @DisplayName("Throws VideoProcessingException when uploadedEpoch is invalid")
        void validate_invalidUploadedEpoch_throwsVideoProcessingException() {
            validMetadata.setUploadedEpoch(0L);
            VideoProcessingException ex = assertThrows(VideoProcessingException.class, () ->
                videoService.validateTranscriptData("some transcript", validMetadata, "https://example.com/video")
            );
            assertTrue(ex.getMessage().contains("uploadedAt timestamp is invalid"));
        }

        @Test
        @DisplayName("Exception message includes all errors when multiple fields are invalid")
        void validate_multipleErrors_messageContainsAll() {
            validMetadata.setTitle(null);
            validMetadata.setDuration(0);
            VideoProcessingException ex = assertThrows(VideoProcessingException.class, () ->
                videoService.validateTranscriptData(null, validMetadata, "https://example.com/video")
            );
            assertTrue(ex.getMessage().contains("transcript text is empty"));
            assertTrue(ex.getMessage().contains("title is missing"));
            assertTrue(ex.getMessage().contains("duration is invalid"));
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
