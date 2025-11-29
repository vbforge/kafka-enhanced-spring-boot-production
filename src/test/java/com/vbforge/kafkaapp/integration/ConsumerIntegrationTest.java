package com.vbforge.kafkaapp.integration;

import com.vbforge.kafkaapp.model.Message;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = { ConsumerIntegrationTest.TOPIC }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class ConsumerIntegrationTest {

    static final String TOPIC = "consumer-test-topic";

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private Consumer<String, Message> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "consumer-test-group-" + UUID.randomUUID(),
                "false",
                embeddedKafkaBroker
        );

        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Message.class.getName());

        DefaultKafkaConsumerFactory<String, Message> consumerFactory =
                new DefaultKafkaConsumerFactory<>(
                        consumerProps,
                        new StringDeserializer(),
                        new JsonDeserializer<>(Message.class, false)
                );

        consumer = consumerFactory.createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, TOPIC);
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void testConsumerReceivesSingleMessage() throws Exception {
        String content = "Consumer integration test";
        Message message = new Message(
                UUID.randomUUID().toString(),
                content,
                LocalDateTime.now()
        );
        // Send message
        kafkaTemplate.send(TOPIC, message.getId(), message).get();

        // Await and assert
        await().atMost(10, SECONDS).untilAsserted(() -> {
            Iterable<ConsumerRecord<String, Message>> records =
                    KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(1)).records(TOPIC);
            assertThat(records).isNotEmpty();

            Message received = StreamSupport.stream(records.spliterator(), false)
                    .filter(r -> r.value().getId().equals(message.getId()))
                    .map(ConsumerRecord::value)
                    .findFirst()
                    .orElseThrow(() ->
                            new AssertionError("Message not received. Records: " + records)
                    );

            assertThat(received.getContent()).isEqualTo(content);
        });
    }



    @Test
    void testConsumerReceivesMultipleMessages() throws Exception {
        int messageCount = 5;

        // Produce multiple messages
        for (int i = 0; i < messageCount; i++) {
            Message message = new Message(
                    "msg-" + i,
                    "Message " + i,
                    LocalDateTime.now()
            );
            kafkaTemplate.send(TOPIC, message.getId(), message).get();
        }

        // Await until all messages are received (max 15s)
        await().atMost(15, SECONDS).untilAsserted(() -> {
            var records = KafkaTestUtils.getRecords(consumer);
            assertThat(records.count()).isEqualTo(messageCount);

            records.forEach(record ->
                    assertThat(record.value().getContent()).contains("Message")
            );
        });
    }
}
