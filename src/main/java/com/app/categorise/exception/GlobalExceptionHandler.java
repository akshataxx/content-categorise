package com.app.categorise.exception;

import com.app.categorise.api.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.nio.file.AccessDeniedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(MethodArgumentNotValidException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex, request);
    }

    @ExceptionHandler(TranscriptNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTranscriptNotFound(TranscriptNotFoundException ex, WebRequest request) {
        logger.warn("Transcript not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex, request);
    }

    @ExceptionHandler(TranscriptDeletionException.class)
    public ResponseEntity<ErrorResponse> handleTranscriptDeletion(TranscriptDeletionException ex, WebRequest request) {
        logger.error("Transcript deletion failed: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex, request);
    }

    @ExceptionHandler(SubscriptionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSubscriptionNotFound(SubscriptionNotFoundException ex, WebRequest request) {
        logger.warn("Subscription not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex, request);
    }

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ErrorResponse> handlePaymentException(PaymentException ex, WebRequest request) {
        logger.error("Payment processing failed: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex, request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException ex, WebRequest request) {
        logger.warn("Rate limit exceeded: {} - Type: {}", ex.getMessage(), ex.getLimitType());
        
        String path = request.getDescription(false).replace("uri=", "");
        ErrorResponse error = new ErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Rate Limit Exceeded",
                ex.getMessage(),
                path
        );
        
        // Build response with rate limit headers
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Remaining", String.valueOf(ex.getRemainingRequests()));
        headers.add("X-RateLimit-Limit-Type", ex.getLimitType().toString());
        
        if (ex.getResetTime() != null) {
            headers.add("X-RateLimit-Reset", String.valueOf(ex.getResetTime().getEpochSecond()));
            headers.add("X-RateLimit-Reset-After", String.valueOf(
                java.time.Duration.between(java.time.Instant.now(), ex.getResetTime()).getSeconds()
            ));
        }
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(error);
    }

    @ExceptionHandler(VideoProcessingException.class)
    public ResponseEntity<ErrorResponse> handleVideoProcessing(VideoProcessingException ex, WebRequest request) {
        logger.error("Video processing failed: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        logger.error("Unexpected error occurred", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex, request);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, Exception ex, WebRequest request) {
        String path = request.getDescription(false).replace("uri=", "");
        ErrorResponse error = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                path
        );
        return ResponseEntity.status(status).body(error);
    }
}

