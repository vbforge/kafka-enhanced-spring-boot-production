# Phase 4: Transactional Messaging 🔐

## 🎯 What You'll Learn

1. **Transactional Producer** - All-or-nothing message sending
2. **Read-Committed Consumer** - Only read committed messages
3. **Exactly-Once Semantics** - No duplicates, no data loss
4. **Transaction Rollback** - Handle failures gracefully
5. **Idempotent Producer** - Prevent duplicate messages

---

## ✅ Phase 4 Checklist

- [ ] Create transaction topics
- [ ] Configure transactional producer
- [ ] Configure read-committed consumer
- [ ] Test successful transaction (commit)
- [ ] Test failed transaction (rollback)
- [ ] Verify idempotence
- [ ] Test exactly-once delivery

---

## 📚 Transaction Basics

### What are Kafka Transactions?

**Without Transactions:**
```
Send Message 1 → ✅ Success
Send Message 2 → ❌ Failure
Send Message 3 → ✅ Success

Result: Partial data (inconsistent state)
```

**With Transactions:**
```
BEGIN TRANSACTION
  Send Message 1 → ✅ 
  Send Message 2 → ❌ 
  Send Message 3 → ✅
ROLLBACK (because of Message 2 failure)

Result: NO messages sent (consistent state)
```

### Use Cases:
- **Bank transfers**: Debit + Credit must both succeed
- **Order processing**: Inventory update + Payment must be atomic
- **Multi-step workflows**: All steps succeed or none

---

## Step 1: Create Transaction Topics

```bash
cd C:\Soft\develop\kafka_2.13-4.1.0

# Topic for transactional messages
bin\windows\kafka-topics.bat --create --topic transactions-topic --bootstrap-server localhost:9092 --partitions 2 --replication-factor 1

# Topic for batch operations
bin\windows\kafka-topics.bat --create --topic batch-operations-topic --bootstrap-server localhost:9092 --partitions 2 --replication-factor 1

# Verify
bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092
```

---

## Step 2: Update application.properties

**Add to**: `src/main/resources/application.properties`

```properties
# Transaction Topics
kafka.topic.transactions=transactions-topic
kafka.topic.batch-operations=batch-operations-topic

# Transaction Configuration
spring.kafka.producer.transaction-id-prefix=tx-
spring.kafka.producer.properties.enable.idempotence=true
```

---

## Step 3: Transactional Producer Configuration

**File**: `src/main/java/com/vbforge/kafkaapp/config/KafkaTransactionalConfig.java`

```java
package com.vbforge.kafkaapp.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.transaction.KafkaTransactionManager;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaTransactionalConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Transactional Producer Factory
     * Key settings:
     * - transactional.id: Required for transactions
     * - enable.idempotence: Prevents duplicates
     * - acks=all: Ensures reliability
     */
    @Bean
    public ProducerFactory<String, Object> transactionalProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Basic Configuration
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Transaction Configuration
        configProps.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "tx-producer-1");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // Reliability Configuration
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Transactional Kafka Template
     * This template enables transaction management
     */
    @Bean("transactionalKafkaTemplate")
    public KafkaTemplate<String, Object> transactionalKafkaTemplate() {
        return new KafkaTemplate<>(transactionalProducerFactory());
    }

    /**
     * Kafka Transaction Manager
     * Enables @Transactional annotation support
     */
    @Bean
    public KafkaTransactionManager<String, Object> kafkaTransactionManager() {
        return new KafkaTransactionManager<>(transactionalProducerFactory());
    }
}
```

---

## Step 4: Read-Committed Consumer Configuration

**Update**: `src/main/java/com/vbforge/kafkaapp/config/KafkaConsumerConfig.java`

Add this method:

```java
/**
 * Read-Committed Consumer Factory
 * Only reads messages that have been committed in a transaction
 */
@Bean
public ConcurrentKafkaListenerContainerFactory<String, Object> transactionalListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
        new ConcurrentKafkaListenerContainerFactory<>();
    
    // Use read-committed isolation level
    Map<String, Object> configProps = new HashMap<>();
    configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "transactional-consumer-group");
    configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    
    // READ_COMMITTED: Only see committed messages
    configProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
    configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    
    JsonDeserializer<Object> jsonDeserializer = new JsonDeserializer<>();
    jsonDeserializer.addTrustedPackages("*");
    
    ConsumerFactory<String, Object> consumerFactory = new DefaultKafkaConsumerFactory<>(
        configProps,
        new StringDeserializer(),
        jsonDeserializer
    );
    
    factory.setConsumerFactory(consumerFactory);
    factory.setConcurrency(1);
    
    return factory;
}
```

---

## Step 5: Transaction Models

**File**: `src/main/java/com/vbforge/kafkaapp/model/Transaction.java`

```java
package com.vbforge.kafkaapp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    private String transactionId;
    private String type; // DEBIT, CREDIT, TRANSFER
    private String accountId;
    private Double amount;
    private LocalDateTime timestamp;
    private String description;
}
```

**File**: `src/main/java/com/vbforge/kafkaapp/dto/BatchTransactionRequest.java`

```java
package com.vbforge.kafkaapp.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchTransactionRequest {

    @NotEmpty(message = "Transaction list cannot be empty")
    @Size(min = 1, max = 10, message = "Batch size must be between 1 and 10")
    private List<TransactionItem> transactions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionItem {
        
        @NotBlank(message = "Account ID is required")
        private String accountId;
        
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be positive")
        private Double amount;
        
        @Pattern(regexp = "^(DEBIT|CREDIT|TRANSFER)$", 
                 message = "Type must be DEBIT, CREDIT, or TRANSFER")
        private String type;
        
        private String description;
    }
}
```

---

## Step 6: Transactional Service

**File**: `src/main/java/com/vbforge/kafkaapp/service/TransactionalService.java`

```java
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
```

---

## Step 7: Transactional Controller

**File**: `src/main/java/com/vbforge/kafkaapp/controller/TransactionController.java`

```java
package com.vbforge.kafkaapp.controller;

import com.vbforge.kafkaapp.dto.BatchTransactionRequest;
import com.vbforge.kafkaapp.model.Transaction;
import com.vbforge.kafkaapp.service.TransactionalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionalService transactionalService;

    /**
     * Send single transaction (always succeeds)
     */
    @PostMapping("/single")
    public ResponseEntity<Map<String, Object>> sendSingleTransaction(
            @RequestParam String accountId,
            @RequestParam Double amount,
            @RequestParam String type) {
        
        Transaction transaction = new Transaction(
            UUID.randomUUID().toString(),
            type,
            accountId,
            amount,
            LocalDateTime.now(),
            "Single transaction"
        );

        String result = transactionalService.sendSingleTransaction(transaction);

        Map<String, Object> response = new HashMap<>();
        response.put("transaction", transaction);
        response.put("status", result);
        response.put("message", "Transaction committed successfully");

        return ResponseEntity.ok(response);
    }

    /**
     * Send batch - ALL SUCCESS scenario
     * All transactions will be committed
     */
    @PostMapping("/batch-success")
    public ResponseEntity<Map<String, Object>> sendBatchSuccess() {
        
        List<Transaction> transactions = List.of(
            new Transaction(UUID.randomUUID().toString(), "DEBIT", "ACC-001", 100.0, 
                LocalDateTime.now(), "Payment 1"),
            new Transaction(UUID.randomUUID().toString(), "CREDIT", "ACC-002", 100.0, 
                LocalDateTime.now(), "Payment 2"),
            new Transaction(UUID.randomUUID().toString(), "TRANSFER", "ACC-003", 50.0, 
                LocalDateTime.now(), "Payment 3")
        );

        log.info("📤 Sending batch of {} transactions (ALL SUCCESS)", transactions.size());

        String result = transactionalService.sendBatchTransactions(transactions);

        Map<String, Object> response = new HashMap<>();
        response.put("transactions_count", transactions.size());
        response.put("status", result);
        response.put("message", "All transactions committed");
        response.put("transactions", transactions);

        return ResponseEntity.ok(response);
    }

    /**
     * Send batch - FAILURE scenario
     * One transaction will fail, causing ALL to rollback
     */
    @PostMapping("/batch-failure")
    public ResponseEntity<Map<String, Object>> sendBatchFailure() {
        
        List<Transaction> transactions = List.of(
            new Transaction(UUID.randomUUID().toString(), "DEBIT", "ACC-001", 100.0, 
                LocalDateTime.now(), "Payment 1 - Will succeed"),
            new Transaction(UUID.randomUUID().toString(), "ERROR", "ACC-002", 100.0, 
                LocalDateTime.now(), "Payment 2 - WILL FAIL"),
            new Transaction(UUID.randomUUID().toString(), "CREDIT", "ACC-003", 50.0, 
                LocalDateTime.now(), "Payment 3 - Never processed")
        );

        log.info("📤 Sending batch of {} transactions (WILL FAIL)", transactions.size());

        try {
            String result = transactionalService.sendBatchTransactions(transactions);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", result);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "ROLLED_BACK");
            response.put("message", "Transaction failed and rolled back");
            response.put("error", e.getMessage());
            response.put("transactions_attempted", transactions.size());
            response.put("transactions_committed", 0);
            response.put("note", "All transactions rolled back - NONE committed to Kafka");
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Custom batch with validation
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> sendCustomBatch(
            @Valid @RequestBody BatchTransactionRequest request) {
        
        List<Transaction> transactions = request.getTransactions().stream()
            .map(item -> new Transaction(
                UUID.randomUUID().toString(),
                item.getType(),
                item.getAccountId(),
                item.getAmount(),
                LocalDateTime.now(),
                item.getDescription()
            ))
            .collect(Collectors.toList());

        log.info("📤 Sending custom batch of {} transactions", transactions.size());

        try {
            String result = transactionalService.sendBatchTransactions(transactions);
            
            Map<String, Object> response = new HashMap<>();
            response.put("transactions_count", transactions.size());
            response.put("status", result);
            response.put("message", "All transactions committed");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "ROLLED_BACK");
            response.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Transaction API is running!");
    }
}
```

---

## Step 8: Transactional Consumer

**File**: `src/main/java/com/vbforge/kafkaapp/consumer/TransactionalConsumer.java`

```java
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
```

---

## 🧪 Testing Transactions

### Test 1: Single Transaction (Success)
```
POST http://localhost:8080/api/transactions/single?accountId=ACC-123&amount=100.50&type=DEBIT
```

**Expected**:
- Message sent to Kafka
- Consumer receives it
- Status: COMMITTED

---

### Test 2: Batch Success (All Commit)
```
POST http://localhost:8080/api/transactions/batch-success
```

**Watch Console**:
```
🔐 Starting BATCH transaction with 3 items
  [1/3] Processing: xxx - DEBIT
  [2/3] Processing: yyy - CREDIT
  [3/3] Processing: zzz - TRANSFER
✅ BATCH transaction COMMITTED
```

**Consumer receives**: ALL 3 transactions

---

### Test 3: Batch Failure (Rollback) **⭐ MOST IMPORTANT TEST**
```
POST http://localhost:8080/api/transactions/batch-failure
```

**Watch Console**:
```
🔐 Starting BATCH transaction with 3 items
  [1/3] Processing: xxx - DEBIT
  [2/3] Processing: yyy - ERROR
❌ BATCH transaction ROLLED BACK
```

**Consumer receives**: ZERO transactions! (All rolled back)

**Response**:
```json
{
  "status": "ROLLED_BACK",
  "message": "Transaction failed and rolled back",
  "transactions_attempted": 3,
  "transactions_committed": 0,
  "note": "All transactions rolled back - NONE committed to Kafka"
}
```

---

### Test 4: Custom Batch (Valid)
```
POST http://localhost:8080/api/transactions/batch
Content-Type: application/json

{
  "transactions": [
    {
      "accountId": "ACC-001",
      "amount": 50.00,
      "type": "DEBIT",
      "description": "Purchase 1"
    },
    {
      "accountId": "ACC-002",
      "amount": 75.50,
      "type": "CREDIT",
      "description": "Refund"
    }
  ]
}
```

---

### Test 5: Custom Batch with Failure
```
POST http://localhost:8080/api/transactions/batch
Content-Type: application/json

{
  "transactions": [
    {
      "accountId": "ACC-001",
      "amount": 50.00,
      "type": "DEBIT",
      "description": "Will succeed"
    },
    {
      "accountId": "ACC-002",
      "amount": 100.00,
      "type": "ERROR",
      "description": "Will cause rollback"
    }
  ]
}
```

**Expected**: Status ROLLED_BACK, consumer receives NOTHING

---

## 🎯 Key Concepts You Learned

### Transaction Guarantees:
1. **Atomicity**: All-or-nothing (all messages sent or none)
2. **Consistency**: System stays in valid state
3. **Isolation**: READ_COMMITTED consumers only see committed data
4. **Durability**: Committed transactions are permanent

### Exactly-Once Semantics:
- **Idempotence**: Same message sent multiple times = stored once
- **Transactional ID**: Identifies producer instance
- **Sequence Numbers**: Prevent duplicates

### When to Use Transactions:
✅ Multi-step workflows  
✅ Financial operations  
✅ Data consistency critical  
❌ High-throughput logging (overkill)  
❌ Fire-and-forget scenarios  

---

## ✅ Phase 4 Completion Checklist

- [ ] Single transaction sent successfully
- [ ] Batch success - all 3 transactions committed
- [ ] Batch failure - all transactions rolled back
- [ ] Consumer with READ_COMMITTED only sees committed messages
- [ ] Rolled back transactions NOT visible to consumer
- [ ] Custom batch with validation tested
- [ ] Understand exactly-once semantics

---

## 🎉 Congratulations on Phase 4!

You now understand:
- ✅ Transactional producers
- ✅ Read-committed consumers
- ✅ Exactly-once delivery
- ✅ Transaction rollback
- ✅ Idempotent producers
- ✅ When to use transactions

---

## 📸 Screenshots to Take

1. Batch success console logs (all committed)
2. Batch failure console logs (rollback)
3. Consumer showing only committed transactions
4. Response showing ROLLED_BACK status

---

## 🚀 Ready for Phase 5?

**Next up (Day 5)**: Testing

Topics:
- Unit tests for services
- Integration tests with TestContainers
- Testing producer/consumer flows
- Testing error scenarios
- Testing transactions

**Reply with**: "Phase 4 complete! Ready for Phase 5!" when you've tested all transaction scenarios! 🎊

---

## 💡 Pro Tips

1. **Always use transactions for financial data**
2. **Test rollback scenarios** - they're critical!
3. **READ_COMMITTED** prevents dirty reads
4. **Idempotence** prevents duplicates on retry
5. **Monitor transaction metrics** in production

---

## 🐛 Troubleshooting

### Issue: Transaction timeout
**Solution**: Increase `transaction.timeout.ms` in producer config

### Issue: Consumer sees uncommitted messages
**Check**: Verify `isolation.level=read_committed` in consumer config

### Issue: Duplicate messages despite idempotence
**Check**: Ensure `transactional.id` is configured

### Issue: Transaction never commits
**Check**: Make sure `@Transactional` annotation is present and transaction manager is configured

---

**You're almost done! One more phase to go!** 🌟