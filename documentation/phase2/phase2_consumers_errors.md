# Phase 2: Consumer Patterns & Error Handling 📥🔄

## 🎯 What You'll Learn

### Part A: Consumer Patterns (1 hour)
1. **Multiple Consumer Groups** - Parallel processing
2. **Consumer with Full Details** - Headers, partition, offset
3. **Manual Offset Control** - When auto-commit isn't enough
4. **Consumer from Specific Offset** - Replay messages

### Part B: Error Handling (1 hour)
5. **Retry Mechanism** - Automatic retries with backoff
6. **Dead Letter Topic (DLT)** - Handle failed messages
7. **Custom Exception Handling** - Recoverable vs Non-recoverable

---

## ✅ Phase 2 Checklist

**Part A: Consumers**
- [ ] Create consumer groups topic
- [ ] Implement multiple consumer groups
- [ ] Consumer with detailed metadata
- [ ] Manual offset management
- [ ] Test all consumer patterns

**Part B: Error Handling**
- [ ] Create DLT topics
- [ ] Configure retry mechanism
- [ ] Implement DLT handler
- [ ] Test retry scenarios
- [ ] Test DLT scenarios

---

## PART A: CONSUMER PATTERNS 📥

### Step 1: Create Topics for Consumer Testing

```bash
cd C:\Soft\develop\kafka_2.13-4.1.0

# Topic for consumer group testing
bin\windows\kafka-topics.bat --create --topic consumer-test-topic --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

# Topic that will have errors (for Part B)
bin\windows\kafka-topics.bat --create --topic error-prone-topic --bootstrap-server localhost:9092 --partitions 2 --replication-factor 1

# Verify
bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092
```

---

### Step 2: Update application.properties

**File**: `src/main/resources/application.properties`

Add new topics:
```properties
# Application Name
spring.application.name=kafka-enhanced-spring-boot-production

# Server Port
server.port=8080

# Kafka Configuration
spring.kafka.bootstrap-servers=localhost:9092

# Producer Configuration
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer

# Consumer Configuration
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=*
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.enable-auto-commit=true
spring.kafka.consumer.auto-commit-interval-ms=5000

# Topic Names
kafka.topic.test=test-topic
kafka.topic.messages=messages-topic
kafka.topic.orders=orders-topic
kafka.topic.consumer-test=consumer-test-topic
kafka.topic.error-prone=error-prone-topic

# Logging
logging.level.com.vbforge.kafkaapp=DEBUG
logging.level.org.apache.kafka=INFO
logging.level.org.springframework.kafka=DEBUG
```

---

### Step 3: Enhanced Consumer Configuration

**File**: `src/main/java/com/vbforge/kafkaapp/config/KafkaConsumerConfig.java`

```java
package com.vbforge.kafkaapp.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Base Consumer Factory - Used by all listeners
     */
    private ConsumerFactory<String, Object> consumerFactory(String groupId) {
        Map<String, Object> configProps = new HashMap<>();
        
        // Basic Configuration
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // Auto offset reset - Start from earliest if no offset exists
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // Read only committed messages (important for transactions)
        configProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        
        // Session and heartbeat configuration
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000); // 30 seconds
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000); // 10 seconds
        
        // Max records per poll
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        
        // Max time between polls
        configProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000); // 5 minutes

        // JSON Deserializer Configuration
        JsonDeserializer<Object> jsonDeserializer = new JsonDeserializer<>();
        jsonDeserializer.addTrustedPackages("*");

        return new DefaultKafkaConsumerFactory<>(
            configProps,
            new StringDeserializer(),
            jsonDeserializer
        );
    }

    /**
     * Standard Kafka Listener Container Factory
     * Used by most consumers with auto-commit
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory("default-group"));
        factory.setConcurrency(3); // 3 consumer threads
        return factory;
    }

    /**
     * Manual Acknowledgment Container Factory
     * Gives you control over when to commit offsets
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> manualAckListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory("manual-ack-group"));
        
        // Enable manual acknowledgment
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        factory.setConcurrency(1);
        return factory;
    }

    /**
     * Batch Listener Container Factory
     * Processes multiple messages at once
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> batchListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory("batch-group"));
        
        // Enable batch listening
        factory.setBatchListener(true);
        
        factory.setConcurrency(1);
        return factory;
    }
}
```

---

### Step 4: Advanced Consumer Patterns

**File**: `src/main/java/com/vbforge/kafkaapp/consumer/AdvancedConsumer.java`

```java
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
```

---

### Step 5: Add Endpoint to Test Consumer Groups

**Update**: `src/main/java/com/vbforge/kafkaapp/controller/ProducerController.java`

Add this method to the existing controller:

```java
/**
 * Send to consumer-test-topic to see multiple consumer groups in action
 */
@PostMapping("/send-to-consumers")
public ResponseEntity<Map<String, Object>> sendToConsumers(@RequestParam(required = false) String content) {
    
    Message message = new Message(
        UUID.randomUUID().toString(),
        content != null ? content : "Testing multiple consumer groups",
        LocalDateTime.now()
    );

    log.info("📤 Sending to consumer-test-topic: {}", message.getContent());

    kafkaTemplate.send("consumer-test-topic", message.getId(), message);

    Map<String, Object> response = new HashMap<>();
    response.put("message", message);
    response.put("note", "This message will be consumed by ALL consumer groups!");
    response.put("consumer_groups", List.of("group-1", "group-2", "detailed-consumer-group", 
                                           "manual-commit-group", "batch-consumer-group"));

    return ResponseEntity.ok(response);
}
```

---

## Test Consumer Patterns (Before Error Handling)

### Test 1: Multiple Consumer Groups
```
POST http://localhost:8080/api/producer/send-to-consumers?content=Hello consumers!
```

**Watch Console** - You'll see the SAME message consumed by different groups!

### Test 2: Send Multiple Messages (for batch)
Send 5-10 messages quickly to see batch processing:
```
POST http://localhost:8080/api/producer/send-to-consumers?content=Batch message 1
POST http://localhost:8080/api/producer/send-to-consumers?content=Batch message 2
POST http://localhost:8080/api/producer/send-to-consumers?content=Batch message 3
```

---

## PART B: ERROR HANDLING 🔄

### Step 6: Create Dead Letter Topic

```bash
cd C:\Soft\develop\kafka_2.13-4.1.0

# Create DLT for error-prone-topic
bin\windows\kafka-topics.bat --create --topic error-prone-topic.DLT --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1

# Verify
bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092
```

---

### Step 7: Error Handler Configuration

**File**: `src/main/java/com/vbforge/kafkaapp/config/KafkaErrorHandlerConfig.java`

```java
package com.vbforge.kafkaapp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExceptionClassifier;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaErrorHandlerConfig {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ConsumerFactory<String, Object> consumerFactory;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Error Handler Container Factory with Retry + DLT
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> errorHandlerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory);
        
        // Configure error handler
        factory.setCommonErrorHandler(createErrorHandler());
        
        factory.setConcurrency(1);
        
        return factory;
    }

    /**
     * Creates Error Handler with:
     * - Retry mechanism (3 attempts with 2 second backoff)
     * - Dead Letter Topic for failed messages
     * - Exception classification (which errors to retry)
     */
    private DefaultErrorHandler createErrorHandler() {
        
        // Dead Letter Publisher - Sends failed messages to .DLT topic
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, exception) -> {
                String dltTopic = record.topic() + ".DLT";
                log.error("💀 Sending to DLT: {} (Original topic: {})", dltTopic, record.topic());
                return new org.apache.kafka.common.TopicPartition(dltTopic, -1);
            }
        );

        // Error Handler with retry
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            recoverer,
            new FixedBackOff(2000L, 3) // 3 retries with 2 second interval
        );

        // Add retry listener to log each attempt
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) -> {
            log.warn("🔄 Retry attempt #{} for message: {} (Error: {})",
                deliveryAttempt,
                record.value(),
                exception.getMessage());
        });

        // Don't retry these exceptions - go straight to DLT
        errorHandler.addNotRetryableExceptions(
            IllegalArgumentException.class,
            NullPointerException.class
        );

        log.info("✅ Error Handler configured: 3 retries, 2s backoff, DLT enabled");
        
        return errorHandler;
    }
}
```

---

### Step 8: Error-Prone Consumer

**File**: `src/main/java/com/vbforge/kafkaapp/consumer/ErrorProneConsumer.java`

```java
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
```

---

### Step 9: Dead Letter Topic Consumer

**File**: `src/main/java/com/vbforge/kafkaapp/consumer/DeadLetterConsumer.java`

```java
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
```

---

### Step 10: Add Error Testing Endpoints

**Update**: `src/main/java/com/vbforge/kafkaapp/controller/ProducerController.java`

Add these methods:

```java
/**
 * Send message that will succeed after 2 retries
 */
@PostMapping("/send-fail-twice")
public ResponseEntity<Map<String, Object>> sendFailTwice() {
    Message message = new Message(
        UUID.randomUUID().toString(),
        "FAIL_TWICE - This will fail 2 times then succeed",
        LocalDateTime.now()
    );

    kafkaTemplate.send("error-prone-topic", message.getId(), message);

    Map<String, Object> response = new HashMap<>();
    response.put("message", message);
    response.put("expected_behavior", "Will retry 2 times then succeed on attempt 3");
    
    return ResponseEntity.ok(response);
}

/**
 * Send message that will always fail and go to DLT
 */
@PostMapping("/send-always-fail")
public ResponseEntity<Map<String, Object>> sendAlwaysFail() {
    Message message = new Message(
        UUID.randomUUID().toString(),
        "ALWAYS_FAIL - This will retry 3 times then go to DLT",
        LocalDateTime.now()
    );

    kafkaTemplate.send("error-prone-topic", message.getId(), message);

    Map<String, Object> response = new HashMap<>();
    response.put("message", message);
    response.put("expected_behavior", "Will retry 3 times (6 seconds total) then send to DLT");
    
    return ResponseEntity.ok(response);
}

/**
 * Send message that fails immediately (non-retryable)
 */
@PostMapping("/send-immediate-fail")
public ResponseEntity<Map<String, Object>> sendImmediateFail() {
    Message message = new Message(
        UUID.randomUUID().toString(),
        "IMMEDIATE_FAIL - Non-retryable error",
        LocalDateTime.now()
    );

    kafkaTemplate.send("error-prone-topic", message.getId(), message);

    Map<String, Object> response = new HashMap<>();
    response.put("message", message);
    response.put("expected_behavior", "Will go directly to DLT without retries");
    
    return ResponseEntity.ok(response);
}
```

---

## 🧪 Testing Phase 2

### Test Consumer Patterns

#### Test 1: Multiple Consumer Groups
```
POST http://localhost:8080/api/producer/send-to-consumers?content=Test message
```
**Expected**: See message consumed by group-1, group-2, detailed-consumer-group, etc.

#### Test 2: Batch Processing
Send 5 messages quickly and watch batch consumer process them together.

---

### Test Error Handling

#### Test 3: Recoverable Error (Retry then Success)
```
POST http://localhost:8080/api/producer/send-fail-twice
```
**Expected Console Output**:
```
Attempt #1 - Simulated failure
(wait 2 seconds)
Attempt #2 - Simulated failure
(wait 2 seconds)
Attempt #3 - SUCCESS!
```

#### Test 4: Always Fail (Go to DLT)
```
POST http://localhost:8080/api/producer/send-always-fail
```
**Expected Console Output**:
```
Attempt #1 - failure
(retry 2 seconds)
Attempt #2 - failure
(retry 2 seconds)
Attempt #3 - failure
Sending to DLT: error-prone-topic.DLT
[DLT Consumer logs the failed message]
```

#### Test 5: Immediate Failure (Non-Retryable)
```
POST http://localhost:8080/api/producer/send-immediate-fail
```
**Expected**: Goes directly to DLT without retries.

---

## 🎯 Key Concepts You Learned

### Consumer Patterns
1. **Multiple Groups** - Same message, different processing
2. **Manual Commit** - Control when offset is committed
3. **Batch Processing** - Process multiple messages at once
4. **Partition Specific** - Target specific partitions

### Error Handling
1. **Automatic Retry** - Transient failures get retried
2. **Backoff Strategy** - Wait between retries
3. **Exception Classification** - Retryable vs non-retryable
4. **Dead Letter Topic** - Capture permanently failed messages

---

## ✅ Phase 2 Completion Checklist

- [ ] Multiple consumer groups working
- [ ] Detailed consumer showing metadata
- [ ] Manual commit consumer tested
- [ ] Batch consumer processing multiple messages
- [ ] Error handler retrying 3 times
- [ ] Messages going to DLT after retries exhausted
- [ ] DLT consumer logging failed messages
- [ ] Non-retryable exceptions going straight to DLT

---

## 🎉 Congratulations on Phase 2!

You now understand:
- ✅ Consumer groups and parallel processing
- ✅ Manual offset control
- ✅ Batch processing
- ✅ Error handling with retries
- ✅ Dead Letter Topic pattern
- ✅ Exception classification

---

## 📸 Screenshots to Take

1. Console showing multiple consumer groups receiving same message
2. Console showing retry attempts (with 2-second delays)
3. Console showing message in DLT
4. Batch consumer processing multiple messages

---

## 🚀 Ready for Phase 3?

**Next up (Day 3)**: Validation & Monitoring

Topics:
- Input validation with Bean Validation
- DTOs (Request/Response objects)
- Global exception handling
- Spring Actuator (health checks)
- Custom metrics
- Structured logging with correlation IDs

**Reply with**: "Phase 2 complete! Ready for Phase 3!" when you've tested all error scenarios! 🎊

---

## 🐛 Troubleshooting

### Issue: Consumer not retrying
**Check**: Make sure you're using `errorHandlerContainerFactory`

### Issue: Messages not going to DLT
**Check**: Verify DLT topic exists: `error-prone-topic.DLT`

### Issue: Can't see retry delays
**Solution**: Watch console logs carefully - there's a 2-second pause between retries

### Issue: Batch consumer not batching
**Solution**: Send messages very quickly (within a few seconds)

---

## 📋 Quick Kafka Commands Reference

```bash
# Check consumer group lag
bin\windows\kafka-consumer-groups.bat --bootstrap-server localhost:9092 --describe --group error-handler-group

# Consume from DLT manually
bin\windows\kafka-console-consumer.bat --topic error-prone-topic.DLT --from-beginning --bootstrap-server localhost:9092

# Reset consumer group (replay messages)
bin\windows\kafka-consumer-groups.bat --bootstrap-server localhost:9092 --group error-handler-group --topic error-prone-topic --reset-offsets --to-earliest --execute
```

---

## 💡 Pro Tips

1. **Watch timing**: Notice the 2-second delays between retries
2. **Check DLT**: After retries exhausted, message appears in DLT immediately
3. **Consumer groups**: Each group maintains its own offset independently
4. **Manual commit**: Use for critical operations (database writes, payments, etc.)
5. **Batch processing**: Great for analytics, bulk inserts, high-throughput scenarios

---

**You're doing great! Phase 2 covers the most important production patterns!** 🎉