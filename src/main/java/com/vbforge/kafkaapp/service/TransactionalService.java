package com.vbforge.kafkaapp.service;

import com.vbforge.kafkaapp.metrics.KafkaMetricsService;
import com.vbforge.kafkaapp.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionalService {

    @Qualifier("transactionalKafkaTemplate")
    private final KafkaTemplate<String, Object> transactionalKafkaTemplate;
    
    private final KafkaMetricsService metricsService;

    @Value("${kafka.topic.transactions}")
    private String transactionsTopic;

    /**
     * Send single transaction (always succeeds)
     */
    @Transactional("kafkaTransactionManager")
    public String sendSingleTransaction(Transaction transaction) {
        log.info("🔐 Starting transaction for: {}", transaction.getTransactionId());
        
        try {
            transactionalKafkaTemplate.send(transactionsTopic, 
                transaction.getTransactionId(), transaction);
            
            log.info("✅ Transaction committed: {}", transaction.getTransactionId());
            metricsService.incrementMessagesSent();
            
            return "COMMITTED";
        } catch (Exception e) {
            log.error("❌ Transaction failed: {}", e.getMessage());
            throw e; // Rollback
        }
    }

    /**
     * Send batch of transactions (all-or-nothing)
     * If ANY transaction fails, ALL are rolled back
     */
    @Transactional("kafkaTransactionManager")
    public String sendBatchTransactions(List<Transaction> transactions) {
        log.info("🔐 Starting BATCH transaction with {} items", transactions.size());
        
        try {
            for (int i = 0; i < transactions.size(); i++) {
                Transaction tx = transactions.get(i);
                
                log.info("  [{}/{}] Processing: {} - {}", 
                    i + 1, transactions.size(), tx.getTransactionId(), tx.getType());
                
                // Simulate failure on ERROR type
                if ("ERROR".equals(tx.getType())) {
                    throw new RuntimeException("Simulated transaction failure on item " + (i + 1));
                }
                
                transactionalKafkaTemplate.send(transactionsTopic, 
                    tx.getTransactionId(), tx);
                
                metricsService.incrementMessagesSent();
                
                // Small delay to see processing
                Thread.sleep(500);
            }
            
            log.info("✅ BATCH transaction COMMITTED - All {} transactions successful", 
                transactions.size());
            
            return "COMMITTED";
            
        } catch (Exception e) {
            log.error("❌ BATCH transaction ROLLED BACK - Error: {}", e.getMessage());
            metricsService.incrementMessagesFailed();
            throw new RuntimeException("Transaction failed and rolled back: " + e.getMessage());
        }
    }

    /**
     * Send with executeInTransaction (alternative approach)
     */
    public String sendWithExecuteInTransaction(List<Transaction> transactions) {
        log.info("🔐 Starting executeInTransaction with {} items", transactions.size());
        
        try {
            transactionalKafkaTemplate.executeInTransaction(kafkaOperations -> {
                for (Transaction tx : transactions) {
                    log.info("  Processing: {}", tx.getTransactionId());
                    
                    if ("ERROR".equals(tx.getType())) {
                        throw new RuntimeException("Simulated error");
                    }
                    
                    kafkaOperations.send(transactionsTopic, tx.getTransactionId(), tx);
                }
                return true;
            });
            
            log.info("✅ Transaction committed via executeInTransaction");
            return "COMMITTED";
            
        } catch (Exception e) {
            log.error("❌ Transaction rolled back: {}", e.getMessage());
            return "ROLLED_BACK";
        }
    }
}