package com.vbforge.kafkaapp.consumer;

import com.vbforge.kafkaapp.metrics.KafkaMetricsService;
import com.vbforge.kafkaapp.model.Transaction;
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
public class TransactionalConsumer {

    private final KafkaMetricsService metricsService;

    /**
     * Consumer with READ_COMMITTED isolation
     * Only sees messages from committed transactions
     */
    @KafkaListener(
        topics = "${kafka.topic.transactions}",
        groupId = "transactional-consumer-group",
        containerFactory = "transactionalListenerContainerFactory"
    )
    public void consumeTransaction(
            ConsumerRecord<String, Transaction> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        Transaction transaction = record.value();
        
        log.info("╔════════════════════════════════════════════════════╗");
        log.info("║     TRANSACTIONAL CONSUMER (READ_COMMITTED)        ║");
        log.info("╠════════════════════════════════════════════════════╣");
        log.info("  Transaction ID: {}", transaction.getTransactionId());
        log.info("  Type: {}", transaction.getType());
        log.info("  Account: {}", transaction.getAccountId());
        log.info("  Amount: ${}", transaction.getAmount());
        log.info("  Description: {}", transaction.getDescription());
        log.info("  Partition: {}", partition);
        log.info("  Offset: {}", offset);
        log.info("╚════════════════════════════════════════════════════╝");
        
        metricsService.incrementMessagesReceived();
        
        // This consumer ONLY sees COMMITTED transactions
        // Rolled-back transactions are NEVER consumed
    }
}