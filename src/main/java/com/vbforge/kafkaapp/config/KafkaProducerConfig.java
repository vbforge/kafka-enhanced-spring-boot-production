package com.vbforge.kafkaapp.config;

import com.vbforge.kafkaapp.model.Message;
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

    //Producer factory with standard configurations
    @Bean
    public ProducerFactory<String, Object> producerFactory(){
        Map<String, Object> configProps = new HashMap<>();

        //basic configs
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        //performance & reliability configs
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);

        //idempotence - prevent duplication
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        //batching for better throughput
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); //16KB
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10); //wait 10 ms to batch messages

        //compression
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        //buffer config
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); //32MB buffer

        return new DefaultKafkaProducerFactory<>(configProps);
    }


    //standard kafka template
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(){
        return new KafkaTemplate<>(producerFactory());
    }

}
