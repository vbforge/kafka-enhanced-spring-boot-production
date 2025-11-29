# Phase 3: Validation & Monitoring 📊✅

## 🎯 What You'll Learn

### Part A: Validation & DTOs (45 min)
1. **Bean Validation** - Validate input automatically
2. **DTOs** - Clean API contracts (Request/Response objects)
3. **Global Exception Handler** - Centralized error handling
4. **Custom Validators** - Business logic validation

### Part B: Monitoring & Health (45 min)
5. **Spring Actuator** - Health checks & metrics
6. **Custom Health Indicators** - Kafka health check
7. **Metrics** - Count messages sent/received
8. **Correlation IDs** - Track requests across services

---

## ✅ Phase 3 Checklist

**Part A: Validation**
- [ ] Add validation dependencies
- [ ] Create DTOs (Request/Response)
- [ ] Add Bean Validation annotations
- [ ] Implement Global Exception Handler
- [ ] Test validation with invalid data

**Part B: Monitoring**
- [ ] Enable Spring Actuator
- [ ] Create Kafka health indicator
- [ ] Add custom metrics
- [ ] Implement correlation ID logging
- [ ] Test health endpoints

---

## PART A: VALIDATION & DTOs ✅

### Step 1: Add Validation Dependencies

**Update**: `pom.xml`

Add after existing dependencies:

```xml
<!-- Bean Validation -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- Actuator for monitoring (Part B) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Micrometer for metrics (Part B) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Rebuild:
```bash
mvn clean install
```

---

### Step 2: Create DTO Package & Classes

**File**: `src/main/java/com/vbforge/kafkaapp/dto/MessageRequest.java`

```java
package com.vbforge.kafkaapp.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {

    @NotBlank(message = "Content cannot be blank")
    @Size(min = 3, max = 500, message = "Content must be between 3 and 500 characters")
    private String content;

    @Min(value = 1, message = "Priority must be at least 1")
    @Max(value = 10, message = "Priority cannot exceed 10")
    private Integer priority;

    @Pattern(regexp = "^(INFO|WARNING|ERROR|DEBUG)$", 
             message = "Type must be one of: INFO, WARNING, ERROR, DEBUG")
    private String type;

    @Email(message = "Invalid email format")
    private String notificationEmail;
}
```

**File**: `src/main/java/com/vbforge/kafkaapp/dto/MessageResponse.java`

```java
package com.vbforge.kafkaapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    
    private String messageId;
    private String content;
    private String status;
    private LocalDateTime timestamp;
    private KafkaMetadata kafkaMetadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KafkaMetadata {
        private String topic;
        private Integer partition;
        private Long offset;
        private Long timestamp;
    }
}
```

**File**: `src/main/java/com/vbforge/kafkaapp/dto/OrderRequest.java`

```java
package com.vbforge.kafkaapp.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    @NotBlank(message = "Customer ID is required")
    @Size(min = 3, max = 50, message = "Customer ID must be between 3 and 50 characters")
    private String customerId;

    @NotBlank(message = "Product is required")
    @Size(min = 2, max = 100, message = "Product name must be between 2 and 100 characters")
    private String product;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "999999.99", message = "Amount cannot exceed 999,999.99")
    private Double amount;

    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 1000, message = "Quantity cannot exceed 1000")
    private Integer quantity;
}
```

**File**: `src/main/java/com/vbforge/kafkaapp/dto/ErrorResponse.java`

```java
package com.vbforge.kafkaapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private Map<String, String> validationErrors;
}
```

---

### Step 3: Global Exception Handler

**File**: `src/main/java/com/vbforge/kafkaapp/exception/GlobalExceptionHandler.java`

```java
package com.vbforge.kafkaapp.exception;

import com.vbforge.kafkaapp.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.KafkaException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle Validation Errors (Bean Validation)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        
        Map<String, String> validationErrors = new HashMap<>();
        
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
        });

        log.error("❌ Validation failed: {}", validationErrors);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message("Invalid input data")
            .path(request.getDescription(false).replace("uri=", ""))
            .validationErrors(validationErrors)
            .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle Kafka Exceptions
     */
    @ExceptionHandler(KafkaException.class)
    public ResponseEntity<ErrorResponse> handleKafkaException(
            KafkaException ex,
            WebRequest request) {
        
        log.error("❌ Kafka error: ", ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Kafka Error")
            .message("Failed to send message to Kafka: " + ex.getMessage())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handle Timeout Exceptions
     */
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeoutException(
            TimeoutException ex,
            WebRequest request) {
        
        log.error("❌ Timeout error: ", ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.REQUEST_TIMEOUT.value())
            .error("Request Timeout")
            .message("Operation timed out: " + ex.getMessage())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(errorResponse);
    }

    /**
     * Handle Generic Exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex,
            WebRequest request) {
        
        log.error("❌ Unexpected error: ", ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Server Error")
            .message("An unexpected error occurred: " + ex.getMessage())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
```

---

### Step 4: Validated Controller with DTOs

**File**: `src/main/java/com/vbforge/kafkaapp/controller/ValidatedProducerController.java`

```java
package com.vbforge.kafkaapp.controller;

import com.vbforge.kafkaapp.dto.MessageRequest;
import com.vbforge.kafkaapp.dto.MessageResponse;
import com.vbforge.kafkaapp.dto.OrderRequest;
import com.vbforge.kafkaapp.model.Message;
import com.vbforge.kafkaapp.model.Order;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestController
@RequestMapping("/api/v2/producer")
@RequiredArgsConstructor
@Validated
public class ValidatedProducerController {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.messages}")
    private String messagesTopic;

    @Value("${kafka.topic.orders}")
    private String ordersTopic;

    /**
     * Send validated message
     * @Valid triggers Bean Validation
     */
    @PostMapping("/send")
    public ResponseEntity<MessageResponse> sendValidatedMessage(
            @Valid @RequestBody MessageRequest request) 
            throws ExecutionException, InterruptedException, TimeoutException {
        
        log.info("📝 Received validated message request: {}", request);

        // Convert DTO to Entity
        Message message = new Message(
            UUID.randomUUID().toString(),
            request.getContent(),
            LocalDateTime.now()
        );

        // Send to Kafka
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(messagesTopic, message.getId(), message);

        SendResult<String, Object> result = future.get(5, TimeUnit.SECONDS);

        // Build Response DTO
        MessageResponse response = MessageResponse.builder()
            .messageId(message.getId())
            .content(message.getContent())
            .status("SENT")
            .timestamp(LocalDateTime.now())
            .kafkaMetadata(MessageResponse.KafkaMetadata.builder()
                .topic(result.getRecordMetadata().topic())
                .partition(result.getRecordMetadata().partition())
                .offset(result.getRecordMetadata().offset())
                .timestamp(result.getRecordMetadata().timestamp())
                .build())
            .build();

        log.info("✅ Message sent successfully: {}", response.getMessageId());

        return ResponseEntity.ok(response);
    }

    /**
     * Send validated order
     */
    @PostMapping("/order")
    public ResponseEntity<MessageResponse> sendValidatedOrder(
            @Valid @RequestBody OrderRequest request) 
            throws ExecutionException, InterruptedException, TimeoutException {
        
        log.info("🛒 Received validated order request: {}", request);

        // Convert DTO to Entity
        Order order = new Order(
            UUID.randomUUID().toString(),
            request.getCustomerId(),
            request.getProduct(),
            request.getAmount(),
            LocalDateTime.now()
        );

        // Send to Kafka with customerId as key
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(ordersTopic, request.getCustomerId(), order);

        SendResult<String, Object> result = future.get(5, TimeUnit.SECONDS);

        // Build Response
        MessageResponse response = MessageResponse.builder()
            .messageId(order.getOrderId())
            .content("Order: " + request.getProduct())
            .status("SENT")
            .timestamp(LocalDateTime.now())
            .kafkaMetadata(MessageResponse.KafkaMetadata.builder()
                .topic(result.getRecordMetadata().topic())
                .partition(result.getRecordMetadata().partition())
                .offset(result.getRecordMetadata().offset())
                .timestamp(result.getRecordMetadata().timestamp())
                .build())
            .build();

        log.info("✅ Order sent successfully: {}", response.getMessageId());

        return ResponseEntity.ok(response);
    }

    /**
     * Health check for validated endpoints
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Validated Producer API is running!");
    }
}
```

---

## Test Validation (Before Part B)

### Test 1: Valid Message
```
POST http://localhost:8080/api/v2/producer/send
Content-Type: application/json

{
  "content": "This is a valid message",
  "priority": 5,
  "type": "INFO",
  "notificationEmail": "user@example.com"
}
```

**Expected**: 200 OK with MessageResponse

### Test 2: Invalid Message (Validation Errors)
```
POST http://localhost:8080/api/v2/producer/send
Content-Type: application/json

{
  "content": "Hi",
  "priority": 15,
  "type": "INVALID",
  "notificationEmail": "not-an-email"
}
```

**Expected**: 400 Bad Request with validation errors:
```json
{
  "timestamp": "2025-11-16T...",
  "status": 400,
  "error": "Validation Failed",
  "validationErrors": {
    "content": "Content must be between 3 and 500 characters",
    "priority": "Priority cannot exceed 10",
    "type": "Type must be one of: INFO, WARNING, ERROR, DEBUG",
    "notificationEmail": "Invalid email format"
  }
}
```

### Test 3: Valid Order
```
POST http://localhost:8080/api/v2/producer/order
Content-Type: application/json

{
  "customerId": "customer-123",
  "product": "Laptop",
  "amount": 999.99,
  "quantity": 2
}
```

### Test 4: Invalid Order
```
POST http://localhost:8080/api/v2/producer/order
Content-Type: application/json

{
  "customerId": "ab",
  "product": "X",
  "amount": -100,
  "quantity": 2000
}
```

**Expected**: 400 with multiple validation errors

---

## PART B: MONITORING & HEALTH 📊

### Step 5: Configure Actuator

**Update**: `src/main/resources/application.properties`

Add at the end:

```properties
# ============================================
# ACTUATOR CONFIGURATION
# ============================================
# Expose all endpoints
management.endpoints.web.exposure.include=health,info,metrics,prometheus,loggers

# Show detailed health information
management.endpoint.health.show-details=always

# Enable Kafka health check
management.health.kafka.enabled=true

# Application Info
info.app.name=@project.name@
info.app.description=Production-ready Kafka Spring Boot Application
info.app.version=@project.version@
info.app.kafka.version=kafka_2.13-4.1.0

# Metrics tags
management.metrics.tags.application=${spring.application.name}
management.metrics.tags.environment=development
```

---

### Step 6: Custom Kafka Health Indicator

**File**: `src/main/java/com/vbforge/kafkaapp/health/KafkaHealthIndicator.java`

```java
package com.vbforge.kafkaapp.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaHealthIndicator implements HealthIndicator {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public Health health() {
        try {
            // Try to get Kafka metrics (quick check)
            var metrics = kafkaTemplate.metrics();
            
            if (metrics != null && !metrics.isEmpty()) {
                return Health.up()
                    .withDetail("status", "Kafka is reachable")
                    .withDetail("broker", "localhost:9092")
                    .withDetail("metrics_count", metrics.size())
                    .build();
            } else {
                return Health.down()
                    .withDetail("status", "Kafka metrics not available")
                    .build();
            }
        } catch (Exception e) {
            log.error("❌ Kafka health check failed", e);
            return Health.down()
                .withDetail("status", "Kafka is NOT reachable")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

---

### Step 7: Custom Metrics Service

**File**: `src/main/java/com/vbforge/kafkaapp/metrics/KafkaMetricsService.java`

```java
package com.vbforge.kafkaapp.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Slf4j
@Service
public class KafkaMetricsService {

    private final Counter messagesSentCounter;
    private final Counter messagesReceivedCounter;
    private final Counter messagesFailedCounter;
    private final Timer messageProcessingTimer;

    public KafkaMetricsService(MeterRegistry registry) {
        // Counter: Total messages sent
        this.messagesSentCounter = Counter.builder("kafka.messages.sent")
            .description("Total number of messages sent to Kafka")
            .tag("type", "producer")
            .register(registry);

        // Counter: Total messages received
        this.messagesReceivedCounter = Counter.builder("kafka.messages.received")
            .description("Total number of messages received from Kafka")
            .tag("type", "consumer")
            .register(registry);

        // Counter: Failed messages
        this.messagesFailedCounter = Counter.builder("kafka.messages.failed")
            .description("Total number of failed messages")
            .tag("type", "error")
            .register(registry);

        // Timer: Message processing time
        this.messageProcessingTimer = Timer.builder("kafka.message.processing.time")
            .description("Time taken to process a message")
            .tag("unit", "milliseconds")
            .register(registry);

        log.info("✅ Kafka metrics initialized");
    }

    public void incrementMessagesSent() {
        messagesSentCounter.increment();
        log.debug("📊 Messages sent counter incremented");
    }

    public void incrementMessagesReceived() {
        messagesReceivedCounter.increment();
        log.debug("📊 Messages received counter incremented");
    }

    public void incrementMessagesFailed() {
        messagesFailedCounter.increment();
        log.warn("📊 Messages failed counter incremented");
    }

    public <T> T recordProcessingTime(Supplier<T> operation) {
        return messageProcessingTimer.record(operation);
    }

    public void recordProcessingTime(Runnable operation) {
        messageProcessingTimer.record(operation);
    }
}
```

---

### Step 8: Update Consumers with Metrics

**Update**: `src/main/java/com/vbforge/kafkaapp/consumer/MessageConsumer.java`

Add metrics tracking:

```java
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
```

---

### Step 9: Update Producer with Metrics

**Update**: `src/main/java/com/vbforge/kafkaapp/controller/ValidatedProducerController.java`

Add metrics injection and tracking:

```java
// Add to class fields
private final KafkaMetricsService metricsService;

// Update sendValidatedMessage method - add after successful send:
metricsService.incrementMessagesSent();

// Update sendValidatedOrder method - add after successful send:
metricsService.incrementMessagesSent();
```

---

### Step 10: Correlation ID Filter

**File**: `src/main/java/com/vbforge/kafkaapp/filter/CorrelationIdFilter.java`

```java
package com.vbforge.kafkaapp.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements Filter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_KEY = "correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        // Get correlation ID from header or generate new one
        String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        
        // Put in MDC (will appear in all logs)
        MDC.put(CORRELATION_ID_KEY, correlationId);
        
        try {
            chain.doFilter(request, response);
        } finally {
            // Clean up
            MDC.remove(CORRELATION_ID_KEY);
        }
    }
}
```

---

### Step 11: Update Logback Configuration

**File**: `src/main/resources/logback-spring.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    
    <!-- Console Appender with Correlation ID -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{correlationId}] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- File Appender -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/kafka-app.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/kafka-app.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{correlationId}] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Application Logs -->
    <logger name="com.vbforge.kafkaapp" level="DEBUG"/>
    
    <!-- Kafka Logs -->
    <logger name="org.apache.kafka" level="INFO"/>
    
    <!-- Spring Kafka Logs -->
    <logger name="org.springframework.kafka" level="INFO"/>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>

</configuration>
```

---

## 🧪 Test Monitoring & Health

### Test 1: Health Endpoint
```
GET http://localhost:8080/actuator/health
```

**Expected Response:**
```json
{
  "status": "UP",
  "components": {
    "kafkaHealthIndicator": {
      "status": "UP",
      "details": {
        "status": "Kafka is reachable",
        "broker": "localhost:9092",
        "metrics_count": 45
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

### Test 2: Metrics Endpoint
```
GET http://localhost:8080/actuator/metrics
```

See all available metrics.

### Test 3: Specific Metric
```
GET http://localhost:8080/actuator/metrics/kafka.messages.sent
```

**Example Response:**
```json
{
  "name": "kafka.messages.sent",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 15.0
    }
  ]
}
```

### Test 4: Correlation ID
Send request with header:
```
POST http://localhost:8080/api/v2/producer/send
X-Correlation-ID: test-12345
Content-Type: application/json

{
  "content": "Test with correlation ID",
  "priority": 5,
  "type": "INFO",
  "notificationEmail": "test@example.com"
}
```

**Check logs** - You'll see `[test-12345]` in every log line!

### Test 5: Prometheus Metrics
```
GET http://localhost:8080/actuator/prometheus
```

See metrics in Prometheus format.

---

## ✅ Phase 3 Completion Checklist

**Validation:**
- [ ] Valid message sent successfully
- [ ] Invalid message returns 400 with validation errors
- [ ] Global exception handler catches all errors
- [ ] DTOs used for clean API contracts

**Monitoring:**
- [ ] Health endpoint shows Kafka status
- [ ] Metrics endpoint accessible
- [ ] Messages sent/received counters incrementing
- [ ] Correlation IDs appearing in logs
- [ ] Prometheus metrics available

---

## 🎯 Key Concepts You Learned

### Validation
1. **Bean Validation** - Automatic input validation
2. **DTOs** - Separate API contracts from domain models
3. **Global Exception Handling** - Centralized error responses
4. **Validation Groups** - Context-specific validation

### Monitoring
1. **Actuator** - Production-ready endpoints
2. **Health Checks** - Monitor application status
3. **Metrics** - Track application behavior
4. **Correlation IDs** - Trace requests across services
5. **Prometheus Integration** - Ready for monitoring stack

---

## 🎉 Congratulations on Phase 3!

You now have:
- ✅ Production-ready input validation
- ✅ Clean API with DTOs
- ✅ Centralized error handling
- ✅ Health checks and monitoring
- ✅ Custom metrics tracking
- ✅ Request tracing with correlation IDs

---

## 📸 Screenshots to Take

1. Validation error response (400 with details)
2. Health endpoint showing Kafka UP
3. Metrics showing message counts
4. Logs with correlation IDs
5. Prometheus metrics output

---

## 🚀 Ready for Phase 4?

**Next up (Day 4)**: Transactions

Topics:
- Transactional producer configuration
- Read-committed consumer
- Exactly-once semantics
- Transaction rollback scenarios
- Idempotent producer

**Reply with**: "Phase 3 complete! Ready for Phase 4!" when you've tested validation and monitoring! 🎊

---

## 💡 Pro Tips

### Validation Tips:
1. **Use @Valid** in controller methods to trigger validation
2. **Create custom validators** for complex business rules
3. **DTOs prevent over-posting** - only accept what you define
4. **Validation groups** let you validate differently based on context

### Monitoring Tips:
1. **Check `/actuator/health` regularly** in production
2. **Export to Prometheus/Grafana** for visualization
3. **Set up alerts** on metrics thresholds
4. **Use correlation IDs** to trace issues across microservices
5. **Monitor consumer lag** to detect processing issues

---

## 🐛 Troubleshooting

### Issue: Validation not working
**Check**: Make sure you have `@Valid` before `@RequestBody`

### Issue: Health endpoint returns 404
**Check**: Verify actuator dependency and exposure configuration

### Issue: Metrics show 0
**Check**: Send some messages first, then check metrics

### Issue: Correlation ID not in logs
**Check**: Verify logback-spring.xml has `%X{correlationId}` pattern

### Issue: Kafka health shows DOWN
**Solution**: Make sure Kafka is running on localhost:9092

---

## 📋 Quick Test Checklist

Run through these before moving to Phase 4:

```
✅ POST valid message → 200 OK
✅ POST invalid message → 400 with validation errors
✅ GET /actuator/health → Shows Kafka UP
✅ GET /actuator/metrics/kafka.messages.sent → Shows count
✅ Check logs for correlation ID pattern: [uuid-here]
✅ Send with X-Correlation-ID header → Appears in logs
```

---

## 📊 What Your Monitoring Looks Like Now

```
Health Check:
├── Kafka: UP ✅
├── Disk Space: UP ✅
└── Ping: UP ✅

Metrics:
├── kafka.messages.sent: 42
├── kafka.messages.received: 38
├── kafka.messages.failed: 2
└── kafka.message.processing.time: avg 15ms

Logs:
2025-11-16 10:30:00 [abc-123] INFO  Producer sent message
2025-11-16 10:30:01 [abc-123] INFO  Consumer received message
```

---

**Excellent progress! You're building production-grade features!** 🌟