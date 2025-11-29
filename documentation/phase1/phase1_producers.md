# Phase 1: Producer Patterns 📤

## 🎯 What You'll Learn

1. **Synchronous Send** - Block and wait for confirmation (reliable but slow)
2. **Asynchronous Send** - Fire-and-forget (fast but no feedback)
3. **Asynchronous with Callback** - Best of both worlds
4. **Send with Keys** - Control message partitioning
5. **Producer Configuration** - Optimize for your use case

---

## ✅ Phase 1 Checklist

- [ ] Update Kafka Producer Configuration
- [ ] Create new topics for testing
- [ ] Implement synchronous send
- [ ] Implement async fire-and-forget
- [ ] Implement async with callback
- [ ] Implement send with message key
- [ ] Test all patterns with Postman
- [ ] Compare performance

---

## Step 1: Create Additional Topics

Open terminal in Kafka directory:
```bash
cd C:\Soft\develop\kafka_2.13-4.1.0
```

### Create topics for different patterns:
```bash
# Topic for sync/async patterns
.\bin\windows\kafka-topics.bat --create --topic messages-topic --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

# Topic for key-based partitioning
.\bin\windows\kafka-topics.bat --create --topic orders-topic --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
```

### Verify topics:
```bash
.\bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092
```

**Expected output:**
```
messages-topic
orders-topic
test-topic
```

---

## Step 2: Enhanced Producer Configuration

**File**: `src/main/java/com/vbforge/kafkaapp/config/KafkaProducerConfig.java`

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

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Producer Factory with optimized configurations
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Basic Configuration
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Performance & Reliability Configuration
        configProps.put(ProducerConfig.ACKS_CONFIG, "all"); // Wait for all replicas (most reliable)
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3); // Retry 3 times on failure
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000); // Wait 1 second between retries
        
        // Idempotence - Prevents duplicate messages
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // Batching for better throughput
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 16KB
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10); // Wait 10ms to batch messages
        
        // Compression
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy"); // Snappy compression
        
        // Buffer Configuration
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 32MB buffer

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Standard Kafka Template
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

**💡 Configuration Explained:**

- **`acks=all`**: Wait for all replicas to acknowledge (most reliable)
- **`retries=3`**: Retry failed sends up to 3 times
- **`enable.idempotence=true`**: Prevents duplicate messages
- **`compression.type=snappy`**: Compress messages for better network efficiency
- **`batch.size` & `linger.ms`**: Batch messages together for better throughput

---

## Step 3: Update application.properties

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

# Topic Names
kafka.topic.test=test-topic
kafka.topic.messages=messages-topic
kafka.topic.orders=orders-topic

# Logging
logging.level.com.vbforge.kafkaapp=DEBUG
logging.level.org.apache.kafka=INFO
```

---

## Step 4: Create Order Model (for key-based routing)

**File**: `src/main/java/com/vbforge/kafkaapp/model/Order.java`

```java
package com.vbforge.kafkaapp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private String orderId;
    private String customerId;
    private String product;
    private Double amount;
    private LocalDateTime orderDate;
}
```

---

## Step 5: Enhanced Producer Controller

**File**: `src/main/java/com/vbforge/kafkaapp/controller/ProducerController.java`

Replace the entire file with:

```java
package com.vbforge.kafkaapp.controller;

import com.vbforge.kafkaapp.model.Message;
import com.vbforge.kafkaapp.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestController
@RequestMapping("/api/producer")
@RequiredArgsConstructor
public class ProducerController {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.messages}")
    private String messagesTopic;

    @Value("${kafka.topic.orders}")
    private String ordersTopic;

    /**
     * ========================================
     * PATTERN 1: SYNCHRONOUS SEND (Blocking)
     * ========================================
     * Pros: You get immediate confirmation
     * Cons: Blocks the thread, slower
     * Use when: You need to know immediately if send succeeded
     */
    @PostMapping("/send-sync")
    public ResponseEntity<Map<String, Object>> sendSync(@RequestParam(required = false) String content) 
            throws ExecutionException, InterruptedException, TimeoutException {
        
        long startTime = System.currentTimeMillis();
        
        Message message = new Message(
            UUID.randomUUID().toString(),
            content != null ? content : "Synchronous message",
            LocalDateTime.now()
        );

        log.info("📤 Sending SYNCHRONOUS message: {}", message.getContent());

        // BLOCKING CALL - Waits for Kafka to acknowledge
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(messagesTopic, message.getId(), message);
        
        // .get() blocks until Kafka responds (or timeout after 5 seconds)
        SendResult<String, Object> result = future.get(5, TimeUnit.SECONDS);
        
        long endTime = System.currentTimeMillis();

        log.info("✅ SYNC SUCCESS - Topic: {}, Partition: {}, Offset: {}, Time: {}ms",
                result.getRecordMetadata().topic(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset(),
                (endTime - startTime));

        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("kafka_metadata", Map.of(
            "topic", result.getRecordMetadata().topic(),
            "partition", result.getRecordMetadata().partition(),
            "offset", result.getRecordMetadata().offset(),
            "timestamp", result.getRecordMetadata().timestamp()
        ));
        response.put("execution_time_ms", endTime - startTime);
        response.put("pattern", "SYNCHRONOUS");

        return ResponseEntity.ok(response);
    }

    /**
     * ========================================
     * PATTERN 2: ASYNCHRONOUS (Fire-and-Forget)
     * ========================================
     * Pros: Very fast, doesn't block
     * Cons: No feedback if it fails
     * Use when: Speed is priority, occasional message loss acceptable
     */
    @PostMapping("/send-async")
    public ResponseEntity<Map<String, Object>> sendAsync(@RequestParam(required = false) String content) {
        
        long startTime = System.currentTimeMillis();
        
        Message message = new Message(
            UUID.randomUUID().toString(),
            content != null ? content : "Asynchronous message (fire-and-forget)",
            LocalDateTime.now()
        );

        log.info("🚀 Sending ASYNCHRONOUS message: {}", message.getContent());

        // NON-BLOCKING - Returns immediately
        kafkaTemplate.send(messagesTopic, message.getId(), message);
        
        long endTime = System.currentTimeMillis();

        log.info("📨 ASYNC sent (no confirmation) - Time: {}ms", (endTime - startTime));

        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("execution_time_ms", endTime - startTime);
        response.put("pattern", "ASYNCHRONOUS_FIRE_AND_FORGET");
        response.put("note", "Message sent but no delivery confirmation");

        return ResponseEntity.ok(response);
    }

    /**
     * ========================================
     * PATTERN 3: ASYNCHRONOUS WITH CALLBACK
     * ========================================
     * Pros: Fast + you get notified of success/failure
     * Cons: Slightly more complex
     * Use when: Best balance of speed and reliability
     * THIS IS THE RECOMMENDED PATTERN!
     */
    @PostMapping("/send-async-callback")
    public ResponseEntity<Map<String, Object>> sendAsyncWithCallback(@RequestParam(required = false) String content) {
        
        long startTime = System.currentTimeMillis();
        
        Message message = new Message(
            UUID.randomUUID().toString(),
            content != null ? content : "Asynchronous message with callback",
            LocalDateTime.now()
        );

        log.info("⚡ Sending ASYNC WITH CALLBACK: {}", message.getContent());

        // NON-BLOCKING with callback
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(messagesTopic, message.getId(), message);

        // Register callback (executed when Kafka responds)
        future.whenComplete((result, exception) -> {
            if (exception == null) {
                // SUCCESS
                log.info("✅ ASYNC CALLBACK SUCCESS - Topic: {}, Partition: {}, Offset: {}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                // FAILURE
                log.error("❌ ASYNC CALLBACK FAILED - Error: {}", exception.getMessage(), exception);
            }
        });

        long endTime = System.currentTimeMillis();

        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("execution_time_ms", endTime - startTime);
        response.put("pattern", "ASYNCHRONOUS_WITH_CALLBACK");
        response.put("note", "Message sent, callback will log result");

        return ResponseEntity.ok(response);
    }

    /**
     * ========================================
     * PATTERN 4: SEND WITH KEY (Partitioning)
     * ========================================
     * Messages with same key go to same partition (ordering guaranteed)
     * Use when: You need message ordering per entity (e.g., all orders for customer X in order)
     */
    @PostMapping("/send-order/{customerId}")
    public ResponseEntity<Map<String, Object>> sendOrder(
            @PathVariable String customerId,
            @RequestParam String product,
            @RequestParam Double amount) 
            throws ExecutionException, InterruptedException, TimeoutException {
        
        Order order = new Order(
            UUID.randomUUID().toString(),
            customerId,
            product,
            amount,
            LocalDateTime.now()
        );

        log.info("🛒 Sending ORDER with key (customerId): {}", customerId);

        // KEY = customerId - All orders from same customer go to same partition
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(ordersTopic, customerId, order);

        SendResult<String, Object> result = future.get(5, TimeUnit.SECONDS);

        log.info("✅ ORDER sent - Customer: {}, Partition: {}, Offset: {}",
                customerId,
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());

        Map<String, Object> response = new HashMap<>();
        response.put("order", order);
        response.put("kafka_metadata", Map.of(
            "key", customerId,
            "topic", result.getRecordMetadata().topic(),
            "partition", result.getRecordMetadata().partition(),
            "offset", result.getRecordMetadata().offset()
        ));
        response.put("pattern", "KEY_BASED_PARTITIONING");
        response.put("note", "All orders for customer " + customerId + " go to partition " + result.getRecordMetadata().partition());

        return ResponseEntity.ok(response);
    }

    /**
     * ========================================
     * COMPARE ALL PATTERNS
     * ========================================
     * Sends same message using all patterns for comparison
     */
    @PostMapping("/compare-patterns")
    public ResponseEntity<Map<String, Object>> comparePatterns(@RequestParam(required = false) String content) 
            throws ExecutionException, InterruptedException, TimeoutException {
        
        String messageContent = content != null ? content : "Pattern comparison test";
        Map<String, Object> results = new HashMap<>();

        // Pattern 1: Sync
        long syncStart = System.currentTimeMillis();
        Message syncMsg = new Message(UUID.randomUUID().toString(), messageContent + " (sync)", LocalDateTime.now());
        kafkaTemplate.send(messagesTopic, syncMsg.getId(), syncMsg).get(5, TimeUnit.SECONDS);
        long syncTime = System.currentTimeMillis() - syncStart;

        // Pattern 2: Async (fire-and-forget)
        long asyncStart = System.currentTimeMillis();
        Message asyncMsg = new Message(UUID.randomUUID().toString(), messageContent + " (async)", LocalDateTime.now());
        kafkaTemplate.send(messagesTopic, asyncMsg.getId(), asyncMsg);
        long asyncTime = System.currentTimeMillis() - asyncStart;

        // Pattern 3: Async with callback
        long callbackStart = System.currentTimeMillis();
        Message callbackMsg = new Message(UUID.randomUUID().toString(), messageContent + " (callback)", LocalDateTime.now());
        kafkaTemplate.send(messagesTopic, callbackMsg.getId(), callbackMsg)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Callback success");
                }
            });
        long callbackTime = System.currentTimeMillis() - callbackStart;

        results.put("synchronous_ms", syncTime);
        results.put("async_fire_and_forget_ms", asyncTime);
        results.put("async_with_callback_ms", callbackTime);
        results.put("fastest", asyncTime < syncTime ? "async" : "sync");
        results.put("recommendation", "Use async-with-callback for best balance");

        log.info("⏱️ Performance Comparison - Sync: {}ms, Async: {}ms, Callback: {}ms", 
                syncTime, asyncTime, callbackTime);

        return ResponseEntity.ok(results);
    }

    /**
     * Health Check
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Producer is running! Ready to send messages.");
    }
}
```

---

## Step 6: Create Consumers for New Topics

**File**: `src/main/java/com/vbforge/kafkaapp/consumer/MessageConsumer.java`

```java
package com.vbforge.kafkaapp.consumer;

import com.vbforge.kafkaapp.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MessageConsumer {

    @KafkaListener(
        topics = "${kafka.topic.messages}",
        groupId = "messages-consumer-group"
    )
    public void consumeMessage(
            ConsumerRecord<String, Message> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
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
    }
}
```

**File**: `src/main/java/com/vbforge/kafkaapp/consumer/OrderConsumer.java`

```java
package com.vbforge.kafkaapp.consumer;

import com.vbforge.kafkaapp.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderConsumer {

    @KafkaListener(
        topics = "${kafka.topic.orders}",
        groupId = "orders-consumer-group"
    )
    public void consumeOrder(
            ConsumerRecord<String, Order> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        Order order = record.value();
        String customerKey = record.key();
        
        log.info("════════════════════════════════════");
        log.info("🛒 ORDER RECEIVED");
        log.info("  Order ID: {}", order.getOrderId());
        log.info("  Customer ID (KEY): {}", customerKey);
        log.info("  Product: {}", order.getProduct());
        log.info("  Amount: ${}", order.getAmount());
        log.info("  Order Date: {}", order.getOrderDate());
        log.info("  ► Partition: {} (All orders for {} go here!)", partition, customerKey);
        log.info("  ► Offset: {}", offset);
        log.info("════════════════════════════════════");
    }
}
```

---

## Step 7: Test with Postman

### Create new Postman Collection: "Kafka Enhanced - Phase 1"

### Test 1: Synchronous Send
```
POST http://localhost:8080/api/producer/send-sync?content=My sync message
```

**Watch the console** - You'll see it's slower but you get immediate confirmation.

### Test 2: Asynchronous (Fire-and-Forget)
```
POST http://localhost:8080/api/producer/send-async?content=My async message
```

**Much faster!** But no delivery guarantee.

### Test 3: Async with Callback (Recommended)
```
POST http://localhost:8080/api/producer/send-async-callback?content=My callback message
```

**Fast + reliable!** Check console for callback log.

### Test 4: Send Order with Key
```
POST http://localhost:8080/api/producer/send-order/customer-123?product=Laptop&amount=999.99
```

Send **multiple orders for the same customer**:
```
POST http://localhost:8080/api/producer/send-order/customer-123?product=Mouse&amount=25.99
POST http://localhost:8080/api/producer/send-order/customer-123?product=Keyboard&amount=75.50
POST http://localhost:8080/api/producer/send-order/customer-456?product=Monitor&amount=299.99
```

**Notice**: All customer-123 orders go to the **same partition**!

### Test 5: Compare All Patterns
```
POST http://localhost:8080/api/producer/compare-patterns?content=Performance test
```

**Check the response** - You'll see the time difference!

---

## Step 8: Verify Partitioning

Check which partition orders went to:

```bash
cd C:\Soft\develop\kafka_2.13-4.1.0

# See all messages in orders-topic
.\bin\windows\kafka-console-consumer.bat --topic orders-topic --from-beginning --bootstrap-server localhost:9092 --property print.key=true --property print.partition=true
```

**You'll see**: All messages with same key (customer ID) are in the same partition!

---

## 🎯 Key Concepts You Learned

### 1. **Synchronous vs Asynchronous**
| Pattern | Speed | Reliability | Use Case |
|---------|-------|-------------|----------|
| Sync | Slow | High | Critical data (payments, etc.) |
| Async | Fast | Low | Logs, analytics |
| Async + Callback | Fast | High | **Best for most cases!** |

### 2. **Message Keys & Partitioning**
- Messages with the **same key** go to the **same partition**
- Ensures **ordering** for related messages
- Example: All orders for customer X are processed in order

### 3. **Producer Configurations**
- `acks=all` - Most reliable (waits for all replicas)
- `enable.idempotence=true` - Prevents duplicates
- `compression=snappy` - Saves network bandwidth
- `retries=3` - Auto-retry on failure

---

## ✅ Phase 1 Completion Checklist

- [ ok ] Created `messages-topic` and `orders-topic`
- [ ok ] Updated `KafkaProducerConfig` with optimizations
- [ ok ] Tested synchronous send (slower but confirmed)
- [ ok ] Tested async fire-and-forget (fastest)
- [ ok ] Tested async with callback (recommended!)
- [ ok ] Tested key-based partitioning (ordering)
- [ ok ] Compared performance of all patterns
- [ ok ] Understood when to use each pattern

---

## 📊 Performance Comparison (Typical Results)

```
Synchronous:        ~15-30ms  ⚠️ Blocks thread
Async (no confirm): ~1-3ms    ⚡ Super fast but risky
Async + Callback:   ~2-5ms    ✅ Best balance!
```

---

## 🐛 Troubleshooting

### Issue: "TimeoutException"
**Cause**: Kafka not responding  
**Solution**: Check if Kafka is running, increase timeout

### Issue: Messages going to wrong partition
**Cause**: Key is null  
**Solution**: Always provide a key when order matters

### Issue: Consumer not receiving messages
**Cause**: Consumer started after messages sent  
**Solution**: Use `--from-beginning` in console consumer, or check offset reset config

---

## 🎉 Congratulations on Phase 1!

You now understand:
- ✅ Different producer patterns and when to use them
- ✅ Message keys and partitioning
- ✅ Performance tradeoffs
- ✅ Producer configuration optimization

---

## 📸 Screenshots to Take

1. Postman response showing execution times
2. Console showing sync vs async timing
3. Console showing orders with same key in same partition
4. Performance comparison endpoint result

---

## 🚀 Ready for Phase 2?

**Next up (Day 2)**: Consumer Patterns + Error Handling

Topics:
- Multiple consumer groups
- Offset management
- Manual vs auto commit
- Error handling with retries
- Dead Letter Topics (DLT)

**Reply with**: "Phase 1 complete! Ready for Phase 2!" when you're done testing! 🎊