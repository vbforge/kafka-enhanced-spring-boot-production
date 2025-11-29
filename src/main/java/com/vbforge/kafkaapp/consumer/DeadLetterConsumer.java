package com.vbforge.kafkaapp.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeadLetterConsumer {

    /**
     * Listens to Dead Letter Topic
     * Here you can:
     * - Log failed messages for investigation
     * - Store in database for manual review
     * - Send alerts to operations team
     * - Attempt alternative processing
     */

    /**
     * To Disable Unused Consumers Temporarily:
     * If we don't need all consumers for testing, we can disable them by commenting out the `@KafkaListener` annotations or adding a profile condition.
     * */
    @KafkaListener(
        topics = "error-prone-topic.DLT",
        groupId = "dlt-consumer-group"
    )
    public void consumeDeadLetter(
            ConsumerRecord<String, Object> record,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage,
            @Header(value = KafkaHeaders.EXCEPTION_STACKTRACE, required = false) String stackTrace,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        
        log.error("╔════════════════════════════════════════════════════╗");
        log.error("║          DEAD LETTER TOPIC - FAILED MESSAGE        ║");
        log.error("╠════════════════════════════════════════════════════╣");
        log.error("  Original Topic: {}", topic);
        log.error("  Failed Message: {}", record.value());
        log.error("  Message Key: {}", record.key());
        log.error("  Partition: {}", record.partition());
        log.error("  Offset: {}", record.offset());
        log.error("  Exception: {}", exceptionMessage);
        log.error("╚════════════════════════════════════════════════════╝");
        
        // TODO: Store in database for manual review
        // TODO: Send alert to operations team
        // TODO: Create incident ticket
        
        log.info("📝 DLT message logged. Manual intervention required.");
    }
}