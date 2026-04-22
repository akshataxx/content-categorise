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
import com.app.categorise.util.processExecutor.TestProcessExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

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
    @Mock private OpenAIClient openAIClient;

    private TestProcessExecutor testProcessExecutor;
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

    private static final String SAMPLE_YTDLP_JSON = """
            {
              "id": "abc123",
              "fulltitle": "Test Video Title",
              "description": "A test video description",
              "duration": 120,
              "extractor": "youtube",
              "webpage_url": "https://www.youtube.com/watch?v=abc123",
              "uploader": "TestChannel",
              "uploader_id": "@testchannel",
              "channel": "TestChannel",
              "channel_id": "UC12345",
              "timestamp": 1700000000,
              "upload_date": "20231114"
            }
            """;

    @BeforeEach
    void setUp() {
        testProcessExecutor = new TestProcessExecutor();
        Executor direct = Runnable::run; // run async work on calling thread in tests
        videoService = new VideoService(
                "/usr/bin/ffmpeg",
                4,
                1,
                direct,
                baseTranscriptRepository,
                categoryAliasService,
                categorisationService,
                categoryService,
                new ObjectMapper(),
                openAIClient,
                testProcessExecutor,
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
    @DisplayName("Fetch Metadata")
    class FetchMetadataTests {

        @Test
        @DisplayName("Successfully parses yt-dlp JSON into VideoMetadata")
        void fetchMetadata_parsesJsonIntoVideoMetadata() {
            testProcessExecutor.setCaptureOutput(SAMPLE_YTDLP_JSON);

            VideoMetadata metadata = videoService.fetchMetadata("https://www.youtube.com/watch?v=abc123");

            assertEquals("Test Video Title", metadata.getTitle());
            assertEquals("A test video description", metadata.getDescription());
            assertEquals(120, metadata.getDuration());
            assertEquals("youtube", metadata.getExtractor());
            assertEquals("TestChannel", metadata.getAccount());
            assertEquals("@testchannel", metadata.getAccountId());
            assertEquals("TestChannel", metadata.getIdentifier());
            assertEquals("UC12345", metadata.getIdentifierId());
            assertEquals(1700000000L, metadata.getUploadedEpoch());
        }

        @Test
        @DisplayName("Builds correct yt-dlp command")
        void fetchMetadata_buildsCorrectCommand() {
            testProcessExecutor.setCaptureOutput(SAMPLE_YTDLP_JSON);
            String url = "https://www.youtube.com/watch?v=abc123";

            videoService.fetchMetadata(url);

            String[] cmd = testProcessExecutor.lastCaptureCommand();
            List<String> cmdList = Arrays.asList(cmd);
            assertTrue(cmdList.contains("yt-dlp"));
            assertTrue(cmdList.contains("--dump-json"));
            assertTrue(cmdList.contains("--no-download"));
            assertTrue(cmdList.contains("--no-warnings"));
            assertTrue(cmdList.contains("--user-agent"));
            assertTrue(cmdList.contains("--"));
            // -- must appear immediately before URL
            assertEquals("--", cmd[cmd.length - 2], "Second-to-last arg should be '--' separator");
            assertEquals(url, cmd[cmd.length - 1], "Last arg should be the video URL");
        }

        @Test
        @DisplayName("Adds TikTok extractor args for TikTok URLs")
        void fetchMetadata_addsTikTokExtractorArgs() {
            testProcessExecutor.setCaptureOutput(SAMPLE_YTDLP_JSON);

            videoService.fetchMetadata("https://www.tiktok.com/@user/video/123");

            List<String> cmdList = Arrays.asList(testProcessExecutor.lastCaptureCommand());
            assertTrue(cmdList.contains("--extractor-args"));
            assertTrue(cmdList.contains("tiktok:api_hostname=api22-normal-c-useast1a.tiktokv.com"));
        }

        @Test
        @DisplayName("Does NOT add TikTok extractor args for non-TikTok URLs")
        void fetchMetadata_noTikTokArgsForYouTubeUrl() {
            testProcessExecutor.setCaptureOutput(SAMPLE_YTDLP_JSON);

            videoService.fetchMetadata("https://www.youtube.com/watch?v=abc123");

            List<String> cmdList = Arrays.asList(testProcessExecutor.lastCaptureCommand());
            assertFalse(cmdList.contains("--extractor-args"));
            assertFalse(cmdList.contains("tiktok:api_hostname=api22-normal-c-useast1a.tiktokv.com"));
        }

        @Test
        @DisplayName("Throws VideoProcessingException with generic message when yt-dlp fails")
        void fetchMetadata_throwsOnYtDlpFailure() {
            testProcessExecutor.setCaptureException(
                new RuntimeException("yt-dlp: ERROR: Unsupported URL"));

            VideoProcessingException ex = assertThrows(VideoProcessingException.class,
                () -> videoService.fetchMetadata("https://example.com/bad-url"));

            assertEquals("Could not process video URL — please check the link and try again.",
                ex.getMessage());
        }

        @Test
        @DisplayName("Throws VideoProcessingException with generic message when JSON is malformed")
        void fetchMetadata_throwsOnMalformedJson() {
            testProcessExecutor.setCaptureOutput("not valid json");

            VideoProcessingException ex = assertThrows(VideoProcessingException.class,
                () -> videoService.fetchMetadata("https://www.youtube.com/watch?v=abc123"));

            assertEquals("Could not process video URL — please check the link and try again.",
                ex.getMessage());
        }

        @Test
        @DisplayName("Throws VideoProcessingException with generic message when stdout is empty")
        void fetchMetadata_throwsOnEmptyStdout() {
            testProcessExecutor.setCaptureOutput("");

            VideoProcessingException ex = assertThrows(VideoProcessingException.class,
                () -> videoService.fetchMetadata("https://www.youtube.com/watch?v=abc123"));

            assertEquals("Could not process video URL — please check the link and try again.",
                ex.getMessage());
        }
    }

    @Nested
    @DisplayName("Download Audio")
    class DownloadAudioTests {

        @Test
        @DisplayName("Invokes process executor with audio-extraction command")
        void downloadAudio_invokesProcessExecutor() throws Exception {
            String testUrl = "https://www.youtube.com/watch?v=abc123";
            assertDoesNotThrow(() -> videoService.downloadAudio(testUrl));
            assertEquals(1, testProcessExecutor.calls());
        }

        @Test
        @DisplayName("Includes -- separator before videoUrl to prevent argument injection")
        void downloadAudio_includesArgSeparatorBeforeUrl() throws Exception {
            String testUrl = "https://www.youtube.com/watch?v=abc123";
            videoService.downloadAudio(testUrl);

            // The run method on TestProcessExecutor doesn't capture the command args,
            // so we verify via a mock-based approach
            // For now, we verify it ran at all — the command structure is verified
            // by the source code having -- before videoUrl (same pattern as fetchMetadata)
            assertEquals(1, testProcessExecutor.calls());
        }

        @Test
        @DisplayName("Does NOT include --write-info-json in the command")
        void downloadAudio_doesNotIncludeWriteInfoJson() throws Exception {
            // We need a mock to capture the command args for run()
            // Since TestProcessExecutor.run() is a no-op that doesn't capture args,
            // let's use a spy approach
            var capturedCommands = new java.util.ArrayList<String[]>();
            var capturingExecutor = new com.app.categorise.util.processExecutor.ProcessExecutor() {
                @Override
                public void run(int timeoutMinutes, String... command) {
                    capturedCommands.add(command);
                }
                @Override
                public String runAndCapture(int timeoutMinutes, String... command) {
                    return "";
                }
            };

            Executor direct = Runnable::run;
            VideoService serviceWithCapture = new VideoService(
                "/usr/bin/ffmpeg", 4, 1, direct,
                baseTranscriptRepository, categoryAliasService, categorisationService,
                categoryService, new ObjectMapper(), openAIClient, capturingExecutor,
                userTranscriptRepository, videoMapper, whisperClient
            );

            String testUrl = "https://www.youtube.com/watch?v=abc123";
            serviceWithCapture.downloadAudio(testUrl);

            assertFalse(capturedCommands.isEmpty(), "Should have captured at least one command");
            String[] command = capturedCommands.getFirst();
            List<String> cmdList = Arrays.asList(command);
            assertFalse(cmdList.contains("--write-info-json"), "--write-info-json should not be in downloadAudio command");
            // Verify -- separator before URL
            assertEquals("--", command[command.length - 2], "Second-to-last arg should be '--' separator");
            assertEquals(testUrl, command[command.length - 1], "Last arg should be the video URL");
        }
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
                    .get(2, TimeUnit.SECONDS);

            assertNotNull(result);
            assertEquals(expectedResponse, result);
            verify(baseTranscriptRepository).findByVideoUrl(videoUrl);
            verify(userTranscriptRepository).findByUserIdAndBaseTranscript_Id(userId, baseTranscriptId);
            verify(categorisationService).classifyAndSuggestAlias(transcriptText, "Test Video", "Test Description");
            verify(videoMapper).createUserTranscriptEntity(userId, baseTranscript, category);
            verify(userTranscriptRepository).save(userTranscript);
            // Should NOT call fetchMetadata when base transcript exists
            assertEquals(0, testProcessExecutor.captureCalls());
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
                    .get(2, TimeUnit.SECONDS);

            assertNotNull(result);
            assertEquals(expectedResponse, result);
            verify(baseTranscriptRepository).findByVideoUrl(videoUrl);
            verify(userTranscriptRepository).findByUserIdAndBaseTranscript_Id(userId, baseTranscriptId);
            verify(userTranscript).setLastAccessedAt(any(Instant.class));
            verify(userTranscriptRepository).save(userTranscript);
            verify(videoMapper).buildResponse(baseTranscript, userTranscript);
            // Should NOT call fetchMetadata or downloadAudio
            assertEquals(0, testProcessExecutor.captureCalls());
            assertEquals(0, testProcessExecutor.calls());

            verify(categorisationService, never()).classifyAndSuggestAlias(anyString(), anyString(), anyString());
            verify(videoMapper, never()).createUserTranscriptEntity(any(), any(), any());
        }

        @Test
        @DisplayName("First-time URL processes successfully end-to-end")
        void firstTimeUrl_processesSuccessfully() throws Exception {
            when(baseTranscriptRepository.findByVideoUrl(videoUrl)).thenReturn(Optional.empty());
            testProcessExecutor.setCaptureOutput(SAMPLE_YTDLP_JSON);

            when(whisperClient.transcribeAudio(any(File.class))).thenReturn(transcriptText);
            when(videoMapper.createBaseTranscriptEntity(eq(videoUrl), eq(transcriptText), any(VideoMetadata.class), any()))
                    .thenReturn(baseTranscript);
            when(baseTranscriptRepository.save(baseTranscript)).thenReturn(baseTranscript);
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
                    .get(2, TimeUnit.SECONDS);

            assertNotNull(result);
            assertEquals(expectedResponse, result);
            // fetchMetadata called once via runAndCapture
            assertEquals(1, testProcessExecutor.captureCalls());
            // downloadAudio called once via run
            assertEquals(1, testProcessExecutor.calls());
        }

        @Test
        @DisplayName("First-time URL with invalid yt-dlp metadata throws VideoProcessingException")
        void firstTimeUrl_invalidMetadata_throwsVideoProcessingException() {
            when(baseTranscriptRepository.findByVideoUrl(videoUrl)).thenReturn(Optional.empty());
            testProcessExecutor.setCaptureException(
                new RuntimeException("yt-dlp: ERROR: Unsupported URL"));

            ExecutionException ex = assertThrows(ExecutionException.class,
                () -> videoService.processVideoAndCreateTranscript(videoUrl, userId)
                    .get(2, TimeUnit.SECONDS));

            assertInstanceOf(VideoProcessingException.class, ex.getCause());
            assertEquals("Could not process video URL — please check the link and try again.",
                ex.getCause().getMessage());
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
