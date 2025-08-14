package com.app.categorise.service;

import com.app.categorise.application.mapper.TranscriptMapper;
import com.app.categorise.data.entity.TranscriptEntity;
import com.app.categorise.data.repository.TranscriptRepository;
import com.app.categorise.domain.service.TranscriptService;
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
import org.springframework.dao.DataAccessException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranscriptServiceTest {

    @Mock
    private TranscriptRepository transcriptRepository;

    @Mock
    private TranscriptMapper transcriptMapper;

    @InjectMocks
    private TranscriptService transcriptService;

    private UUID transcriptId1;
    private UUID transcriptId2;

    @BeforeEach
    void setUp() {
        transcriptId1 = UUID.randomUUID();
        transcriptId2 = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Delete Transcripts")
    class DeleteTranscriptsTests {

        @Test
        @DisplayName("Should delete multiple transcripts successfully")
        void deleteTranscripts_WithValidIds_CallsRepositoryDeleteAllById() {
            // Arrange
            List<UUID> transcriptIds = Arrays.asList(transcriptId1, transcriptId2);
            List<TranscriptEntity> existingEntities = Arrays.asList(
                    createMockEntity(transcriptId1),
                    createMockEntity(transcriptId2)
            );

            when(transcriptRepository.findAllById(transcriptIds)).thenReturn(existingEntities);

            // Act
            transcriptService.deleteTranscripts(transcriptIds);

            // Assert
            verify(transcriptRepository).findAllById(transcriptIds);
            verify(transcriptRepository).deleteAllById(transcriptIds);
        }

        @Test
        @DisplayName("Should delete single transcript successfully")
        void deleteTranscripts_WithSingleId_CallsRepositoryDeleteAllById() {
            // Arrange
            List<UUID> transcriptIds = Collections.singletonList(transcriptId1);
            List<TranscriptEntity> existingEntities = Collections.singletonList(createMockEntity(transcriptId1));

            when(transcriptRepository.findAllById(transcriptIds)).thenReturn(existingEntities);

            // Act
            transcriptService.deleteTranscripts(transcriptIds);

            // Assert
            verify(transcriptRepository).findAllById(transcriptIds);
            verify(transcriptRepository).deleteAllById(transcriptIds);
        }

        @Test
        @DisplayName("Should throw exception for empty transcript IDs list")
        void deleteTranscripts_WithEmptyList_ThrowsIllegalArgumentException() {
            // Arrange
            List<UUID> transcriptIds = Collections.emptyList();

            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                transcriptService.deleteTranscripts(transcriptIds);
            });

            assertEquals("Transcript IDs list cannot be null or empty", exception.getMessage());
            verify(transcriptRepository, never()).findAllById(any());
            verify(transcriptRepository, never()).deleteAllById(any());
        }

        @Test
        @DisplayName("Should throw exception for null transcript IDs list")
        void deleteTranscripts_WithNullList_ThrowsIllegalArgumentException() {
            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                transcriptService.deleteTranscripts(null);
            });

            assertEquals("Transcript IDs list cannot be null or empty", exception.getMessage());
            verify(transcriptRepository, never()).findAllById(any());
            verify(transcriptRepository, never()).deleteAllById(any());
        }

        @Test
        @DisplayName("Should throw exception when some transcripts don't exist")
        void deleteTranscripts_WithNonExistentIds_ThrowsTranscriptNotFoundException() {
            // Arrange
            List<UUID> transcriptIds = Arrays.asList(transcriptId1, transcriptId2);
            List<TranscriptEntity> existingEntities = Collections.singletonList(createMockEntity(transcriptId1));

            when(transcriptRepository.findAllById(transcriptIds)).thenReturn(existingEntities);

            // Act & Assert
            TranscriptNotFoundException exception = assertThrows(TranscriptNotFoundException.class, () -> {
                transcriptService.deleteTranscripts(transcriptIds);
            });

            assertTrue(exception.getMessage().contains("not found"));
            assertTrue(exception.getMessage().contains(transcriptId2.toString()));
            assertEquals(Collections.singletonList(transcriptId2), exception.getNotFoundIds());
            verify(transcriptRepository).findAllById(transcriptIds);
            verify(transcriptRepository, never()).deleteAllById(any());
        }

        @Test
        @DisplayName("Should handle unexpected runtime exceptions")
        void deleteTranscripts_RepositoryThrowsRuntimeException_ThrowsTranscriptDeletionException() {
            // Arrange
            List<UUID> transcriptIds = Arrays.asList(transcriptId1, transcriptId2);
            List<TranscriptEntity> existingEntities = Arrays.asList(
                    createMockEntity(transcriptId1),
                    createMockEntity(transcriptId2)
            );

            when(transcriptRepository.findAllById(transcriptIds)).thenReturn(existingEntities);
            doThrow(new RuntimeException("Unexpected error")).when(transcriptRepository).deleteAllById(transcriptIds);

            // Act & Assert
            TranscriptDeletionException exception = assertThrows(TranscriptDeletionException.class, () -> {
                transcriptService.deleteTranscripts(transcriptIds);
            });

            assertTrue(exception.getMessage().contains("Unexpected error"));
            assertEquals(transcriptIds, exception.getFailedIds());
            verify(transcriptRepository).findAllById(transcriptIds);
            verify(transcriptRepository).deleteAllById(transcriptIds);
        }
    }

    private TranscriptEntity createMockEntity(UUID id) {
        TranscriptEntity entity = new TranscriptEntity();
        entity.setId(id);
        entity.setVideoUrl("https://example.com/video");
        entity.setTranscript("Test transcript");
        return entity;
    }
}