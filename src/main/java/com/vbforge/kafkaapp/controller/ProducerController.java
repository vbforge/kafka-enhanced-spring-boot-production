package com.vbforge.kafkaapp.controller;

import com.vbforge.kafkaapp.model.Message;
import com.vbforge.kafkaapp.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.protocol.types.Field;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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
     * Health Check
     */
    @GetMapping("/health") //api/producer/health
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Producer is running! Ready to send messages.");
    }

    /**
     * ========================================
     * PATTERN 1: SYNCHRONOUS SEND (Blocking)
     * ========================================
     * Pros: You get immediate confirmation
     * Cons: Blocks the thread, slower
     * Use when: You need to know immediately if send succeeded
     */
    @PostMapping("/send-sync") //api/producer/send-sync
    public ResponseEntity<Map<String, Object>> sendSync(@RequestParam(required = false) String content) throws ExecutionException, InterruptedException, TimeoutException {
        long startTime = System.currentTimeMillis();

        Message message = new Message(
                UUID.randomUUID().toString(),
                content != null ? content : "Synchronous message",
                LocalDateTime.now()
        );

        log.info("Sending synchronous message: {}", message);

        //blocking call - wait till kafka proceed all acknowledge
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(messagesTopic, message.getId(), message);

        //.get() blocks until Kafka responds (or timeout after 5 seconds)
        SendResult<String, Object> result = future.get(5, TimeUnit.SECONDS);

        long finishTime = System.currentTimeMillis();

        log.info("SYNC SUCCESS - Topic: {}, Partition: {}, Offset: {}, Time: {}ms",
                result.getRecordMetadata().topic(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset(),
                (finishTime - startTime)
        );

        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("kafka_metadata", Map.of(
                "topic", result.getRecordMetadata().topic(),
                "partition", result.getRecordMetadata().partition(),
                "offset", result.getRecordMetadata().offset(),
                "timestamp", result.getRecordMetadata().timestamp()
        ));
        response.put("execution_time_ms", finishTime - startTime);
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
    public ResponseEntity<Map<String, Object>> sendAsync(@RequestParam(required = false) String content){
        long startTime = System.currentTimeMillis();

        Message message = new Message(
                UUID.randomUUID().toString(),
                content != null ? content : "Asynchronous message (fire & forget)",
                LocalDateTime.now()
        );

        log.info("Sending ASYNCHRONOUS message: {}", message.getContent());

        //NON-BLOCKING - Returns immediately
        kafkaTemplate.send(messagesTopic, message.getId(), message);

        long finishTime = System.currentTimeMillis();

        log.info("📨 ASYNC sent (no confirmation) - Time: {}ms", (finishTime - startTime));

        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("execution_time_ms", finishTime - startTime);
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
    @PostMapping("/send-async-callback") //api/producer/send-async-callback
    public ResponseEntity<Map<String, Object>> sendAsyncWithCallback(@RequestParam(required = false) String content){
        long startTime = System.currentTimeMillis();

        Message message = new Message(
                UUID.randomUUID().toString(),
                content != null ? content : "Asynchronous message with callback",
                LocalDateTime.now()
        );

        log.info("Sending ASYNC WITH CALLBACK: {}", message.getContent());

        //NON-BLOCKING with callback
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(messagesTopic, message.getId(), message);

        //Register callback (executed when Kafka responds)
        future.whenComplete((result, e)->{
            if(e == null){
                //success
                log.info("ASYNC CALLBACK SUCCESS - Topic: {}, Partition: {}, Offset: {}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }else {
                //fail
                log.error("ASYNC CALLBACK FAILED - Error: {}", e.getMessage(), e);
            }
        });

        long finishTime = System.currentTimeMillis();

        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("execution_time_ms", finishTime - startTime);
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
            @RequestParam Double amount) throws ExecutionException, InterruptedException, TimeoutException {

        Order order = new Order(
                UUID.randomUUID().toString(),
                customerId,
                product,
                amount,
                LocalDateTime.now());

        log.info("Sending ORDER with key (customerId): {}", customerId);

        //KEY = customerId - All orders from same customer go to same partition
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(ordersTopic, customerId, order);

        SendResult<String, Object> result = future.get(5, TimeUnit.SECONDS);

        log.info("ORDER sent - Customer: {}, Partition: {}, Offset: {}",
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






}





















