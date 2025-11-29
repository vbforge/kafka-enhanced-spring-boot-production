package com.vbforge.kafkaapp.consumer;

import com.vbforge.kafkaapp.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class AdvancedConsumer {

    /**
     * PATTERN 1: Multiple Consumer Groups
     * Same messages are processed by different groups independently
     */
    @KafkaListener(
            topics = "${kafka.topic.consumer-test}",
            groupId = "group-1"
    )
    public void consumerGroup1(Message message) {
        log.info("👥 [GROUP-1] Received: {}", message.getContent());
        // Simulate processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @KafkaListener(
            topics = "${kafka.topic.consumer-test}",
            groupId = "group-2"
    )
    public void consumerGroup2(Message message) {
        log.info("👥 [GROUP-2] Received: {}", message.getContent());
        // Different processing logic
        // Simulate processing a bit longer (2 sec)
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * PATTERN 2: Consumer with Full Metadata
     * Access all message details: partition, offset, timestamp, headers
     */
    @KafkaListener(
            topics = "${kafka.topic.consumer-test}",
            groupId = "detailed-consumer-group"
    )
    public void consumerWithDetails(
            ConsumerRecord<String, Message> record,
            @Payload Message message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("📋 DETAILED CONSUMER");
        log.info("  Topic: {}", topic);
        log.info("  Partition: {}", partition);
        log.info("  Offset: {}", offset);
        log.info("  Timestamp: {}", timestamp);
        log.info("  Key: {}", record.key());
        log.info("  Message ID: {}", message.getId());
        log.info("  Message Content: {}", message.getContent());
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * PATTERN 3: Manual Offset Control
     * You decide when to commit the offset
     * Use when: Processing must succeed before committing
     */
    @KafkaListener(
            topics = "${kafka.topic.consumer-test}",
            groupId = "manual-commit-group",
            containerFactory = "manualAckListenerContainerFactory"
    )
    public void manualCommitConsumer(Message message, Acknowledgment acknowledgment) {
        try {
            log.info("🖐️ [MANUAL-COMMIT] Processing: {}", message.getContent());

            // Simulate processing (database save, API call, etc.)
            processMessage(message);

            // Only commit if processing succeeded
            acknowledgment.acknowledge();
            log.info("✅ [MANUAL-COMMIT] Offset committed for: {}", message.getId());

        } catch (Exception e) {
            log.error("❌ [MANUAL-COMMIT] Processing failed, offset NOT committed: {}", e.getMessage());
            // Offset not committed - message will be reprocessed
        }
    }

    /**
     * PATTERN 4: Batch Consumer
     * Process multiple messages at once (better performance)
     */
    @KafkaListener(
            topics = "${kafka.topic.consumer-test}",
            groupId = "batch-consumer-group",
            containerFactory = "batchListenerContainerFactory"
    )
    public void batchConsumer(List<ConsumerRecord<String, Message>> records) {
        log.info("📦 [BATCH] Received {} messages", records.size());

        records.forEach(record -> {
            Message message = record.value();
            log.info("  → Processing: {} (Partition: {}, Offset: {})",
                    message.getContent(), record.partition(), record.offset());
        });

        // Batch processing (e.g., bulk database insert)
        log.info("✅ [BATCH] Processed {} messages in one batch", records.size());
    }

    /**
     * PATTERN 5: Consume from Specific Partition and Offset
     * Useful for replaying messages or consuming from known point
     */
    @KafkaListener(
            groupId = "partition-specific-group",
            topicPartitions = @TopicPartition(
                    topic = "${kafka.topic.consumer-test}",
                    partitions = {"0"},
                    partitionOffsets = @PartitionOffset(partition = "1", initialOffset = "0")
            )
    )
    public void specificPartitionConsumer(Message message,
                                          @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        log.info("🎯 [PARTITION-{}] Received from specific partition: {}", partition, message.getContent());
    }


    /**
     * Helper method to simulate message processing
     */
    private void processMessage(Message message) throws Exception {
        // Simulate processing time
        Thread.sleep(50);

        // Simulate occasional failure (10% chance)
        if (Math.random() < 0.1) {
            throw new Exception("Random processing failure");
        }
    }

}

















