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
