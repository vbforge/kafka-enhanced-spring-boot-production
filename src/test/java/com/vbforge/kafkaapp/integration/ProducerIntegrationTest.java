package com.vbforge.kafkaapp.integration;

import com.vbforge.kafkaapp.model.Message;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"test-topic"},
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:9093",
                "port=9093"
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=localhost:9093"
})
public class ProducerIntegrationTest {

    private static final String TEST_TOPIC = "test-topic";

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaMessageListenerContainer<String, Message> container;
    private BlockingQueue<ConsumerRecord<String, Message>> records;


    @BeforeEach
    void setUp() {
        // Create consumer for testing
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9093");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        DefaultKafkaConsumerFactory<String, Message> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps,
                        new StringDeserializer(),
                        new JsonDeserializer<>(Message.class));

        ContainerProperties containerProps = new ContainerProperties(TEST_TOPIC);

        container = new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
        records = new LinkedBlockingQueue<>();

        container.setupMessageListener((MessageListener<String, Message>) records::add);
        container.start();

        ContainerTestUtils.waitForAssignment(container, 1);
    }

    @AfterEach
    void tearDown() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void testSendMessage_Successfully() throws InterruptedException {
        // Given
        Message message = new Message(
                UUID.randomUUID().toString(),
                "Integration test message",
                LocalDateTime.now()
        );

        // When
        kafkaTemplate.send(TEST_TOPIC, message.getId(), message);

        // Then
        ConsumerRecord<String, Message> received = records.poll(10, TimeUnit.SECONDS);

        assertThat(received).isNotNull();
        assertThat(received.value().getId()).isEqualTo(message.getId());
        assertThat(received.value().getContent()).isEqualTo(message.getContent());
    }

    @Test
    void testSendMultipleMessages() throws InterruptedException {
        // Given
        int messageCount = 5;

        // When
        for (int i = 0; i < messageCount; i++) {
            Message message = new Message(
                    "msg-" + i,
                    "Message " + i,
                    LocalDateTime.now()
            );
            kafkaTemplate.send(TEST_TOPIC, message.getId(), message);
        }

        // Then
        for (int i = 0; i < messageCount; i++) {
            ConsumerRecord<String, Message> received = records.poll(10, TimeUnit.SECONDS);
            assertThat(received).isNotNull();
        }
    }

}
