package com.vbforge.kafkaapp.controller;

import com.vbforge.kafkaapp.dto.MessageRequest;
import com.vbforge.kafkaapp.dto.MessageResponse;
import com.vbforge.kafkaapp.dto.OrderRequest;
import com.vbforge.kafkaapp.metrics.KafkaMetricsService;
import com.vbforge.kafkaapp.model.Message;
import com.vbforge.kafkaapp.model.Order;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestController
@RequestMapping("/api/v2/producer")
@RequiredArgsConstructor
@Validated
public class ValidatedProducerController {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaMetricsService metricsService;

    @Value("${kafka.topic.messages}")
    private String messagesTopic;

    @Value("${kafka.topic.orders}")
    private String ordersTopic;

    /**
     * Send validated message
     * @Valid triggers Bean Validation
     */
    @PostMapping("/send")
    public ResponseEntity<MessageResponse> sendValidatedMessage(
            @Valid @RequestBody MessageRequest request) 
            throws ExecutionException, InterruptedException, TimeoutException {
        
        log.info("📝 Received validated message request: {}", request);

        // Convert DTO to Entity
        Message message = new Message(
            UUID.randomUUID().toString(),
            request.getContent(),
            LocalDateTime.now()
        );

        // Send to Kafka
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(messagesTopic, message.getId(), message);

        SendResult<String, Object> result = future.get(5, TimeUnit.SECONDS);

        // Build Response DTO
        MessageResponse response = MessageResponse.builder()
            .messageId(message.getId())
            .content(message.getContent())
            .status("SENT")
            .timestamp(LocalDateTime.now())
            .kafkaMetadata(MessageResponse.KafkaMetadata.builder()
                .topic(result.getRecordMetadata().topic())
                .partition(result.getRecordMetadata().partition())
                .offset(result.getRecordMetadata().offset())
                .timestamp(result.getRecordMetadata().timestamp())
                .build())
            .build();

        log.info("✅ Message sent successfully: {}", response.getMessageId());

        metricsService.incrementMessagesSent();

        return ResponseEntity.ok(response);
    }

    /**
     * Send validated order
     */
    @PostMapping("/order")
    public ResponseEntity<MessageResponse> sendValidatedOrder(
            @Valid @RequestBody OrderRequest request) 
            throws ExecutionException, InterruptedException, TimeoutException {
        
        log.info("🛒 Received validated order request: {}", request);

        // Convert DTO to Entity
        Order order = new Order(
            UUID.randomUUID().toString(),
            request.getCustomerId(),
            request.getProduct(),
            request.getAmount(),
            LocalDateTime.now()
        );

        // Send to Kafka with customerId as key
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(ordersTopic, request.getCustomerId(), order);

        SendResult<String, Object> result = future.get(5, TimeUnit.SECONDS);

        // Build Response
        MessageResponse response = MessageResponse.builder()
            .messageId(order.getOrderId())
            .content("Order: " + request.getProduct())
            .status("SENT")
            .timestamp(LocalDateTime.now())
            .kafkaMetadata(MessageResponse.KafkaMetadata.builder()
                .topic(result.getRecordMetadata().topic())
                .partition(result.getRecordMetadata().partition())
                .offset(result.getRecordMetadata().offset())
                .timestamp(result.getRecordMetadata().timestamp())
                .build())
            .build();

        log.info("✅ Order sent successfully: {}", response.getMessageId());

        metricsService.incrementMessagesSent();

        return ResponseEntity.ok(response);
    }

    /**
     * Health check for validated endpoints
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Validated Producer API is running!");
    }
}