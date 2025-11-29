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
//import org.springframework.kafka.support.ExceptionClassifier;

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