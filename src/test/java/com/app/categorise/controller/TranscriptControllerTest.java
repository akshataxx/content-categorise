package com.app.categorise.controller;

import com.app.categorise.api.controller.TranscriptController;
import com.app.categorise.api.dto.DeleteTranscriptsRequest;
import com.app.categorise.application.mapper.TranscriptMapper;
import com.app.categorise.domain.service.CategoryAliasService;
import com.app.categorise.domain.service.CategoryService;
import com.app.categorise.domain.service.TranscriptService;
import com.app.categorise.exception.TranscriptNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = TranscriptController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
class TranscriptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TranscriptService transcriptService;

    @MockitoBean
    private CategoryAliasService categoryAliasService;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private TranscriptMapper transcriptMapper;

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
        void deleteTranscripts_WithValidIds_ReturnsNoContent() throws Exception {
            // Arrange
            DeleteTranscriptsRequest request = new DeleteTranscriptsRequest();
            request.setTranscriptIds(Arrays.asList(transcriptId1, transcriptId2));

            // Act & Assert
            mockMvc.perform(delete("/transcript")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            verify(transcriptService).deleteTranscripts(Arrays.asList(transcriptId1, transcriptId2));
        }

        @Test
        @DisplayName("Should delete single transcript successfully")
        void deleteTranscripts_WithSingleId_ReturnsNoContent() throws Exception {
            // Arrange
            DeleteTranscriptsRequest request = new DeleteTranscriptsRequest();
            request.setTranscriptIds(Collections.singletonList(transcriptId1));

            // Act & Assert
            mockMvc.perform(delete("/transcript")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            verify(transcriptService).deleteTranscripts(Collections.singletonList(transcriptId1));
        }

        @Test
        @DisplayName("Should return bad request for empty transcript IDs list")
        void deleteTranscripts_WithEmptyList_ReturnsBadRequest() throws Exception {
            // Arrange
            DeleteTranscriptsRequest request = new DeleteTranscriptsRequest();
            request.setTranscriptIds(Collections.emptyList());

            // Act & Assert
            mockMvc.perform(delete("/transcript")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(transcriptService, never()).deleteTranscripts(any());
        }

        @Test
        @DisplayName("Should return bad request for null transcript IDs list")
        void deleteTranscripts_WithNullList_ReturnsBadRequest() throws Exception {
            // Arrange
            DeleteTranscriptsRequest request = new DeleteTranscriptsRequest();
            request.setTranscriptIds(null);

            // Act & Assert
            mockMvc.perform(delete("/transcript")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(transcriptService, never()).deleteTranscripts(any());
        }

        @Test
        @DisplayName("Should return bad request for too many transcript IDs")
        void deleteTranscripts_WithTooManyIds_ReturnsBadRequest() throws Exception {
            // Arrange - Create a list with 101 IDs
            DeleteTranscriptsRequest request = new DeleteTranscriptsRequest();
            request.setTranscriptIds(Collections.nCopies(101, UUID.randomUUID()));

            // Act & Assert
            mockMvc.perform(delete("/transcript")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(transcriptService, never()).deleteTranscripts(any());
        }

        @Test
        @DisplayName("Should return bad request for empty request body")
        void deleteTranscripts_WithEmptyRequestBody_ReturnsBadRequest() throws Exception {
            // Act & Assert
            mockMvc.perform(delete("/transcript")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest());

            verify(transcriptService, never()).deleteTranscripts(any());
        }

        @Test
        @DisplayName("Should return internal server error for invalid JSON")
        void deleteTranscripts_WithInvalidJson_ReturnsInternalServerError() throws Exception {
            // Act & Assert - JSON parsing errors result in 500 from global exception handler
            mockMvc.perform(delete("/transcript")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("invalid json"))
                    .andExpect(status().isInternalServerError());

            verify(transcriptService, never()).deleteTranscripts(any());
        }

        @Test
        @DisplayName("Should return internal server error for missing content type")
        void deleteTranscripts_WithMissingContentType_ReturnsInternalServerError() throws Exception {
            // Arrange
            DeleteTranscriptsRequest request = new DeleteTranscriptsRequest();
            request.setTranscriptIds(Collections.singletonList(transcriptId1));

            // Act & Assert - Missing content type results in 500 from global exception handler
            mockMvc.perform(delete("/transcript")
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError());

            verify(transcriptService, never()).deleteTranscripts(any());
        }

        @Test
        @DisplayName("Should return not found when transcripts don't exist")
        void deleteTranscripts_ServiceThrowsTranscriptNotFoundException_ReturnsNotFound() throws Exception {
            // Arrange
            DeleteTranscriptsRequest request = new DeleteTranscriptsRequest();
            request.setTranscriptIds(Arrays.asList(transcriptId1, transcriptId2));

            doThrow(new TranscriptNotFoundException("Transcript not found", Collections.singletonList(transcriptId2)))
                    .when(transcriptService).deleteTranscripts(any());

            // Act & Assert
            mockMvc.perform(delete("/transcript")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Transcript not found"))
                    .andExpect(jsonPath("$.status").value(404));

            verify(transcriptService).deleteTranscripts(Arrays.asList(transcriptId1, transcriptId2));
        }

        @Test
        @DisplayName("Should return bad request for illegal arguments")
        void deleteTranscripts_ServiceThrowsIllegalArgumentException_ReturnsBadRequest() throws Exception {
            // Arrange
            DeleteTranscriptsRequest request = new DeleteTranscriptsRequest();
            request.setTranscriptIds(Arrays.asList(transcriptId1, transcriptId2));

            doThrow(new IllegalArgumentException("Invalid request"))
                    .when(transcriptService).deleteTranscripts(any());

            // Act & Assert
            mockMvc.perform(delete("/transcript")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid request"))
                    .andExpect(jsonPath("$.status").value(400));

            verify(transcriptService).deleteTranscripts(Arrays.asList(transcriptId1, transcriptId2));
        }
    }
}