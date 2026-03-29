package com.app.categorise.domain.service;

import com.app.categorise.application.mapper.VideoMapper;
import com.app.categorise.api.dto.TranscriptDtoWithAliases;
import com.app.categorise.data.entity.BaseTranscriptEntity;
import com.app.categorise.data.entity.CategoryEntity;
import com.app.categorise.data.entity.UserTranscriptEntity;
import com.app.categorise.data.repository.BaseTranscriptRepository;
import com.app.categorise.data.repository.UserTranscriptRepository;
import com.app.categorise.exception.TranscriptDeletionException;
import com.app.categorise.exception.TranscriptNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranscriptServiceTest {

    @Mock
    private UserTranscriptRepository userTranscriptRepository;

    @Mock
    private VideoMapper videoMapper;

    @InjectMocks
    private TranscriptService transcriptService;

    private UUID userTranscriptId1;
    private UUID userTranscriptId2;
    private UUID userId;
    private UUID baseTranscriptId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        userTranscriptId1 = UUID.randomUUID();
        userTranscriptId2 = UUID.randomUUID();
        userId = UUID.randomUUID();
        baseTranscriptId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Find Transcript")
    class FindTranscriptTests {

        @Test
        @DisplayName("Should return transcript when found by id and userId")
        void findTranscript_WithValidIdAndUserId_ReturnsTranscript() {
            // Arrange
            UserTranscriptEntity entity = createMockUserTranscriptEntity(userTranscriptId1);
            TranscriptDtoWithAliases expectedDto = mock(TranscriptDtoWithAliases.class);

            when(userTranscriptRepository.findByIdAndUserId(userTranscriptId1, userId))
                    .thenReturn(Optional.of(entity));
            when(videoMapper.buildResponse(entity.getBaseTranscript(), entity))
                    .thenReturn(expectedDto);

            // Act
            Optional<TranscriptDtoWithAliases> result = transcriptService.findTranscript(userTranscriptId1, userId);

            // Assert
            assertTrue(result.isPresent());
            assertEquals(expectedDto, result.get());
            verify(userTranscriptRepository).findByIdAndUserId(userTranscriptId1, userId);
        }

        @Test
        @DisplayName("Should return empty when transcript belongs to different user (IDOR prevention)")
        void findTranscript_WithDifferentUserId_ReturnsEmpty() {
            // Arrange
            UUID otherUserId = UUID.randomUUID();
            when(userTranscriptRepository.findByIdAndUserId(userTranscriptId1, otherUserId))
                    .thenReturn(Optional.empty());

            // Act
            Optional<TranscriptDtoWithAliases> result = transcriptService.findTranscript(userTranscriptId1, otherUserId);

            // Assert
            assertTrue(result.isEmpty());
            verify(userTranscriptRepository).findByIdAndUserId(userTranscriptId1, otherUserId);
            verify(videoMapper, never()).buildResponse(any(), any(UserTranscriptEntity.class));
        }

        @Test
        @DisplayName("Should return empty when transcript id does not exist")
        void findTranscript_WithNonExistentId_ReturnsEmpty() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(userTranscriptRepository.findByIdAndUserId(nonExistentId, userId))
                    .thenReturn(Optional.empty());

            // Act
            Optional<TranscriptDtoWithAliases> result = transcriptService.findTranscript(nonExistentId, userId);

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty when userTranscriptId is null")
        void findTranscript_WithNullId_ReturnsEmpty() {
            // Act
            Optional<TranscriptDtoWithAliases> result = transcriptService.findTranscript(null, userId);

            // Assert
            assertTrue(result.isEmpty());
            verify(userTranscriptRepository, never()).findByIdAndUserId(any(), any());
        }
    }


    @Nested
    @DisplayName("All Filtered Transcripts")
    class AllFilteredTranscriptsTests {

        @Test
        @DisplayName("Returns valid transcripts and excludes malformed ones")
        void allFilteredTranscripts_excludesMalformedRecords() {
            UserTranscriptEntity valid = createMockUserTranscriptEntity(userTranscriptId1);
            valid.getBaseTranscript().setDuration(60.0);
            valid.getBaseTranscript().setUploadedAt(Instant.ofEpochSecond(1700000000L));

            UserTranscriptEntity noTitle = createMockUserTranscriptEntity(userTranscriptId2);
            noTitle.getBaseTranscript().setTitle(null);
            noTitle.getBaseTranscript().setDuration(60.0);
            noTitle.getBaseTranscript().setUploadedAt(Instant.ofEpochSecond(1700000000L));

            UUID id3 = UUID.randomUUID();
            UserTranscriptEntity zeroDuration = createMockUserTranscriptEntity(id3);
            zeroDuration.getBaseTranscript().setDuration(0.0);
            zeroDuration.getBaseTranscript().setUploadedAt(Instant.ofEpochSecond(1700000000L));

            when(userTranscriptRepository.filterByUser(userId, null, null, null, null))
                    .thenReturn(List.of(valid, noTitle, zeroDuration));

            TranscriptDtoWithAliases dto = mock(TranscriptDtoWithAliases.class);
            when(videoMapper.buildResponse(valid.getBaseTranscript(), valid)).thenReturn(dto);

            List<TranscriptDtoWithAliases> results = transcriptService.allFilteredTranscripts(userId, null, null, null, null);

            assertEquals(1, results.size());
            assertEquals(dto, results.get(0));
            verify(videoMapper, never()).buildResponse(noTitle.getBaseTranscript(), noTitle);
            verify(videoMapper, never()).buildResponse(zeroDuration.getBaseTranscript(), zeroDuration);
        }

        @Test
        @DisplayName("Returns all transcripts when all are valid")
        void allFilteredTranscripts_allValid_returnsAll() {
            UserTranscriptEntity e1 = createMockUserTranscriptEntity(userTranscriptId1);
            e1.getBaseTranscript().setDuration(60.0);
            e1.getBaseTranscript().setUploadedAt(Instant.ofEpochSecond(1700000000L));
            UserTranscriptEntity e2 = createMockUserTranscriptEntity(userTranscriptId2);
            e2.getBaseTranscript().setDuration(90.0);
            e2.getBaseTranscript().setUploadedAt(Instant.ofEpochSecond(1700000000L));

            when(userTranscriptRepository.filterByUser(userId, null, null, null, null))
                    .thenReturn(List.of(e1, e2));
            when(videoMapper.buildResponse(any(BaseTranscriptEntity.class), any(UserTranscriptEntity.class)))
                    .thenReturn(mock(TranscriptDtoWithAliases.class));

            List<TranscriptDtoWithAliases> results = transcriptService.allFilteredTranscripts(userId, null, null, null, null);

            assertEquals(2, results.size());
        }
    }
    @Nested
    @DisplayName("Delete Transcripts")
    class DeleteTranscriptsTests {

        @Test
        @DisplayName("Should delete multiple user transcripts successfully")
        void deleteTranscripts_WithValidIds_CallsRepositoryDeleteAllById() {
            // Arrange
            List<UUID> userTranscriptIds = Arrays.asList(userTranscriptId1, userTranscriptId2);
            List<UserTranscriptEntity> existingEntities = Arrays.asList(
                    createMockUserTranscriptEntity(userTranscriptId1),
                    createMockUserTranscriptEntity(userTranscriptId2)
            );

            when(userTranscriptRepository.findAllById(userTranscriptIds)).thenReturn(existingEntities);

            // Act
            transcriptService.deleteTranscripts(userTranscriptIds);

            // Assert
            verify(userTranscriptRepository).findAllById(userTranscriptIds);
            verify(userTranscriptRepository).deleteAllById(userTranscriptIds);
        }

        @Test
        @DisplayName("Should delete single user transcript successfully")
        void deleteTranscripts_WithSingleId_CallsRepositoryDeleteAllById() {
            // Arrange
            List<UUID> userTranscriptIds = Collections.singletonList(userTranscriptId1);
            List<UserTranscriptEntity> existingEntities = Collections.singletonList(createMockUserTranscriptEntity(userTranscriptId1));

            when(userTranscriptRepository.findAllById(userTranscriptIds)).thenReturn(existingEntities);

            // Act
            transcriptService.deleteTranscripts(userTranscriptIds);

            // Assert
            verify(userTranscriptRepository).findAllById(userTranscriptIds);
            verify(userTranscriptRepository).deleteAllById(userTranscriptIds);
        }

        @Test
        @DisplayName("Should throw exception for empty user transcript IDs list")
        void deleteTranscripts_WithEmptyList_ThrowsIllegalArgumentException() {
            // Arrange
            List<UUID> userTranscriptIds = Collections.emptyList();

            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                transcriptService.deleteTranscripts(userTranscriptIds);
            });

            assertEquals("User transcript IDs list cannot be null or empty", exception.getMessage());
            verify(userTranscriptRepository, never()).findAllById(any());
            verify(userTranscriptRepository, never()).deleteAllById(any());
        }

        @Test
        @DisplayName("Should throw exception for null user transcript IDs list")
        void deleteTranscripts_WithNullList_ThrowsIllegalArgumentException() {
            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                transcriptService.deleteTranscripts(null);
            });

            assertEquals("User transcript IDs list cannot be null or empty", exception.getMessage());
            verify(userTranscriptRepository, never()).findAllById(any());
            verify(userTranscriptRepository, never()).deleteAllById(any());
        }

        @Test
        @DisplayName("Should throw exception when some user transcripts don't exist")
        void deleteTranscripts_WithNonExistentIds_ThrowsTranscriptNotFoundException() {
            // Arrange
            List<UUID> userTranscriptIds = Arrays.asList(userTranscriptId1, userTranscriptId2);
            List<UserTranscriptEntity> existingEntities = Collections.singletonList(createMockUserTranscriptEntity(userTranscriptId1));

            when(userTranscriptRepository.findAllById(userTranscriptIds)).thenReturn(existingEntities);

            // Act & Assert
            TranscriptNotFoundException exception = assertThrows(TranscriptNotFoundException.class, () -> {
                transcriptService.deleteTranscripts(userTranscriptIds);
            });

            assertTrue(exception.getMessage().contains("not found"));
            assertTrue(exception.getMessage().contains(userTranscriptId2.toString()));
            assertEquals(Collections.singletonList(userTranscriptId2), exception.getNotFoundIds());
            verify(userTranscriptRepository).findAllById(userTranscriptIds);
            verify(userTranscriptRepository, never()).deleteAllById(any());
        }

        @Test
        @DisplayName("Should handle unexpected runtime exceptions")
        void deleteTranscripts_RepositoryThrowsRuntimeException_ThrowsTranscriptDeletionException() {
            // Arrange
            List<UUID> userTranscriptIds = Arrays.asList(userTranscriptId1, userTranscriptId2);
            List<UserTranscriptEntity> existingEntities = Arrays.asList(
                    createMockUserTranscriptEntity(userTranscriptId1),
                    createMockUserTranscriptEntity(userTranscriptId2)
            );

            when(userTranscriptRepository.findAllById(userTranscriptIds)).thenReturn(existingEntities);
            doThrow(new RuntimeException("Unexpected error")).when(userTranscriptRepository).deleteAllById(userTranscriptIds);

            // Act & Assert
            TranscriptDeletionException exception = assertThrows(TranscriptDeletionException.class, () -> {
                transcriptService.deleteTranscripts(userTranscriptIds);
            });

            assertTrue(exception.getMessage().contains("Unexpected error"));
            assertEquals(userTranscriptIds, exception.getFailedIds());
            verify(userTranscriptRepository).findAllById(userTranscriptIds);
            verify(userTranscriptRepository).deleteAllById(userTranscriptIds);
        }
    }

    private UserTranscriptEntity createMockUserTranscriptEntity(UUID id) {
        UserTranscriptEntity entity = new UserTranscriptEntity();
        entity.setId(id);
        entity.setUserId(userId);
        
        // Create mock base transcript
        BaseTranscriptEntity baseTranscript = new BaseTranscriptEntity();
        baseTranscript.setId(baseTranscriptId);
        baseTranscript.setVideoUrl("https://example.com/video");
        baseTranscript.setTranscript("Test transcript");
        baseTranscript.setTitle("Test Title");
        baseTranscript.setDescription("Test Description");
        baseTranscript.setCreatedAt(Instant.now());
        
        // Create mock category
        CategoryEntity category = new CategoryEntity();
        category.setId(categoryId);
        category.setName("Test Category");
        
        entity.setBaseTranscript(baseTranscript);
        entity.setCategory(category);
        entity.setCreatedAt(Instant.now());
        entity.setLastAccessedAt(Instant.now());
        
        return entity;
    }
}