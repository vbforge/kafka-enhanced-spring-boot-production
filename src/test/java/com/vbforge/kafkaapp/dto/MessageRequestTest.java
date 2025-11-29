package com.vbforge.kafkaapp.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MessageRequestTest {

    private Validator validator;

    @BeforeEach
    public void setup(){
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    public void testValidMessageRequest(){
        MessageRequest request = new MessageRequest(
                "Valid content here",
                5,
                "INFO",
                "test@example.com"
        );

        Set<ConstraintViolation<MessageRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void testInvalidContent_TooShort() {
        // Given
        MessageRequest request = new MessageRequest(
                "Hi", // Too short
                5,
                "INFO",
                "test@example.com"
        );

        // When
        Set<ConstraintViolation<MessageRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("between 3 and 500");
    }

    @Test
    void testInvalidPriority_TooHigh() {
        // Given
        MessageRequest request = new MessageRequest(
                "Valid content",
                15, // Too high
                "INFO",
                "test@example.com"
        );

        // When
        Set<ConstraintViolation<MessageRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("cannot exceed 10");
    }

    @Test
    void testInvalidType() {
        // Given
        MessageRequest request = new MessageRequest(
                "Valid content",
                5,
                "INVALID_TYPE",
                "test@example.com"
        );

        // When
        Set<ConstraintViolation<MessageRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("INFO, WARNING, ERROR, DEBUG");
    }

    @Test
    void testInvalidEmail() {
        // Given
        MessageRequest request = new MessageRequest(
                "Valid content",
                5,
                "INFO",
                "not-an-email"
        );

        // When
        Set<ConstraintViolation<MessageRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("email");
    }

    @Test
    void testMultipleValidationErrors() {
        // Given
        MessageRequest request = new MessageRequest(
                "Hi", // Too short
                20,  // Too high
                "BAD", // Invalid type
                "bad-email" // Invalid email
        );

        // When
        Set<ConstraintViolation<MessageRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).hasSize(4); // All 4 validations should fail
    }


}