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

    public static final String DEFAULT_GROUP = "default-group";
    public static final String MANUAL_ACK_GROUP = "manual-ack-group";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Base Consumer Factory - Used by all listeners
     */
    private ConsumerFactory<String, Object> consumerFactory(String groupId){
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
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(){
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory(DEFAULT_GROUP));
        factory.setConcurrency(3); //3 threads
        return factory;
    }

    /**
     * Manual Acknowledgment Container Factory
     * Gives you control over when to commit offsets
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> manualAckListenerContainerFactory(){
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory(MANUAL_ACK_GROUP));

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
    public ConcurrentKafkaListenerContainerFactory<String, Object> batchListenerContainerFactory(){
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory("batch-group"));

        // Enable batch listening
        factory.setBatchListener(true);

        factory.setConcurrency(1);
        return factory;
    }

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


}












