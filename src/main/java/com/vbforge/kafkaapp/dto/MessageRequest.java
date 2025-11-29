package com.vbforge.kafkaapp.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {

    @NotBlank(message = "Content cannot be blank")
    @Size(min = 3, max = 500, message = "Content must be between 3 and 500 characters")
    private String content;

    @Min(value = 1, message = "Priority must be at least 1")
    @Max(value = 10, message = "Priority cannot exceed 10")
    private Integer priority;

    @Pattern(regexp = "^(INFO|WARNING|ERROR|DEBUG)$", 
             message = "Type must be one of: INFO, WARNING, ERROR, DEBUG")
    private String type;

    @Email(message = "Invalid email format")
    private String notificationEmail;
}