package com.vbforge.kafkaapp.exception;

import com.vbforge.kafkaapp.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.KafkaException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle Validation Errors (Bean Validation)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        
        Map<String, String> validationErrors = new HashMap<>();
        
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
        });

        log.error("❌ Validation failed: {}", validationErrors);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message("Invalid input data")
            .path(request.getDescription(false).replace("uri=", ""))
            .validationErrors(validationErrors)
            .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle Kafka Exceptions
     */
    @ExceptionHandler(KafkaException.class)
    public ResponseEntity<ErrorResponse> handleKafkaException(
            KafkaException ex,
            WebRequest request) {
        
        log.error("❌ Kafka error: ", ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Kafka Error")
            .message("Failed to send message to Kafka: " + ex.getMessage())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handle Timeout Exceptions
     */
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeoutException(
            TimeoutException ex,
            WebRequest request) {
        
        log.error("❌ Timeout error: ", ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.REQUEST_TIMEOUT.value())
            .error("Request Timeout")
            .message("Operation timed out: " + ex.getMessage())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(errorResponse);
    }

    /**
     * Handle Generic Exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex,
            WebRequest request) {
        
        log.error("❌ Unexpected error: ", ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Server Error")
            .message("An unexpected error occurred: " + ex.getMessage())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}