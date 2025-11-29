package com.vbforge.kafkaapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    
    private String messageId;
    private String content;
    private String status;
    private LocalDateTime timestamp;
    private KafkaMetadata kafkaMetadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KafkaMetadata {
        private String topic;
        private Integer partition;
        private Long offset;
        private Long timestamp;
    }
}