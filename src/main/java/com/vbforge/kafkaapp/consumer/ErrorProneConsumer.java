package com.vbforge.kafkaapp.consumer;

import com.vbforge.kafkaapp.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class ErrorProneConsumer {

    private final AtomicInteger attemptCounter = new AtomicInteger(0);

    /**
     * Consumer that intentionally fails to demonstrate error handling
     * - First 3 attempts will fail (triggers retry)
     * - After 3 retries, message goes to DLT
     */
    @KafkaListener(
        topics = "${kafka.topic.error-prone}",
        groupId = "error-handler-group",
        containerFactory = "errorHandlerContainerFactory"
    )
    public void consumeWithError(
            Message message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        int attempt = attemptCounter.incrementAndGet();
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("⚠️  ERROR-PRONE CONSUMER - Attempt #{}", attempt);
        log.info("  Message: {}", message.getContent());
        log.info("  Partition: {}, Offset: {}", partition, offset);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Simulate different error scenarios based on message content
        if (message.getContent().contains("IMMEDIATE_FAIL")) {
            // Non-retryable exception - goes straight to DLT
            throw new IllegalArgumentException("Non-retryable error - going to DLT immediately");
        }
        
        if (message.getContent().contains("ALWAYS_FAIL")) {
            // Retryable exception - will retry 3 times then go to DLT
            throw new RuntimeException("Retryable error - will retry 3 times");
        }
        
        if (message.getContent().contains("FAIL_TWICE")) {
            // Fail first 2 times, succeed on 3rd
            if (attempt <= 2) {
                log.error("❌ Attempt {} - Simulated failure", attempt);
                throw new RuntimeException("Simulated temporary failure");
            }
            log.info("✅ Attempt {} - SUCCESS!", attempt);
            attemptCounter.set(0); // Reset for next message
        }
        
        // Success case
        log.info("✅ Message processed successfully: {}", message.getContent());
        attemptCounter.set(0); // Reset counter
    }
}