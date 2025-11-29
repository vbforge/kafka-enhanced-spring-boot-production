package com.vbforge.kafkaapp.consumer;

import com.vbforge.kafkaapp.metrics.KafkaMetricsService;
import com.vbforge.kafkaapp.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageConsumer {

    private final KafkaMetricsService metricsService;

    @KafkaListener(
            topics = "${kafka.topic.messages}",
            groupId = "messages-consumer-group"
    )
    public void consumeMessage(
            ConsumerRecord<String, Message> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        // Record metrics
        metricsService.recordProcessingTime(() -> {
            Message message = record.value();

            log.info("====================================");
            log.info("📨 MESSAGE RECEIVED");
            log.info("  ID: {}", message.getId());
            log.info("  Content: {}", message.getContent());
            log.info("  Timestamp: {}", message.getTimestamp());
            log.info("  Kafka Key: {}", record.key());
            log.info("  Partition: {}", partition);
            log.info("  Offset: {}", offset);
            log.info("====================================");

            metricsService.incrementMessagesReceived();
        });
    }
}