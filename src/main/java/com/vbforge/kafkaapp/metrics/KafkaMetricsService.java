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