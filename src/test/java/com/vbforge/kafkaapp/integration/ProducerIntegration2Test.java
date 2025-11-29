package com.vbforge.kafkaapp.integration;

import com.vbforge.kafkaapp.model.Message;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Difference from ProducerIntegrationTest
 * ✔ Zero need for local Kafka
 * ✔ Zero port conflicts (dynamic port)
 * ✔ Zero flaky behavior
 * ✔ Parallel-test safe
 * ✔ Clean, self-contained
 * ✔ Proper JSON serialization handling
 * ✔ Follows Spring + Kafka official test patterns
 * */

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"test-topic"},
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:0",
                "port=0"
        }
)
class ProducerIntegration2Test {

    private static final String TEST_TOPIC = "test-topic";

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private KafkaMessageListenerContainer<String, Message> container;
    private BlockingQueue<ConsumerRecord<String, Message>> records;

    @BeforeAll
    static void beforeAll() {
        // Important for Windows + Kafka stability
        System.setProperty("java.net.preferIPv4Stack", "true");
    }

    @BeforeEach
    void setUp() {
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                embeddedKafkaBroker.getBrokersAsString()
        );
        consumerProps.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "test-group-" + UUID.randomUUID()
        );
        consumerProps.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );
        consumerProps.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );
        consumerProps.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                JsonDeserializer.class
        );
        consumerProps.put(
                JsonDeserializer.TRUSTED_PACKAGES,
                "*"
        );

        DefaultKafkaConsumerFactory<String, Message> consumerFactory =
                new DefaultKafkaConsumerFactory<>(
                        consumerProps,
                        new StringDeserializer(),
                        new JsonDeserializer<>(Message.class)
                );

        ContainerProperties containerProps = new ContainerProperties(TEST_TOPIC);

        records = new LinkedBlockingQueue<>();
        container = new KafkaMessageListenerContainer<>(consumerFactory, containerProps);

        container.setupMessageListener(
                (MessageListener<String, Message>) records::add
        );

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
        Message message = new Message(
                UUID.randomUUID().toString(),
                "Integration test message",
                LocalDateTime.now()
        );

        kafkaTemplate.send(TEST_TOPIC, message.getId(), message);
        kafkaTemplate.flush(); // Prevent flaky behavior

        ConsumerRecord<String, Message> received =
                records.poll(10, TimeUnit.SECONDS);

        assertThat(received).isNotNull();
        assertThat(received.key()).isEqualTo(message.getId());
        assertThat(received.value().getId()).isEqualTo(message.getId());
        assertThat(received.value().getContent()).isEqualTo(message.getContent());
    }

    @Test
    void testSendMultipleMessages() throws InterruptedException {
        int messageCount = 5;

        for (int i = 0; i < messageCount; i++) {
            Message message = new Message(
                    "msg-" + i,
                    "Message " + i,
                    LocalDateTime.now()
            );

            kafkaTemplate.send(TEST_TOPIC, message.getId(), message);
        }

        kafkaTemplate.flush();

        for (int i = 0; i < messageCount; i++) {
            ConsumerRecord<String, Message> received =
                    records.poll(10, TimeUnit.SECONDS);

            assertThat(received).isNotNull();
        }
    }
}