package com.vbforge.kafkaapp.integration;

import com.vbforge.kafkaapp.model.Message;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = { "error-prone-topic", "error-prone-topic.DLT" },
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:9092",
                "port=9092"
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "kafka.topic.error-prone=error-prone-topic"
})
class ErrorHandlingIntegrationTest {

    private static final String MAIN_TOPIC = "error-prone-topic";
    private static final String DLT_TOPIC = "error-prone-topic.DLT";

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private DefaultKafkaConsumerFactory<String, Message> dltConsumerFactory() {

        Map<String, Object> props =
                KafkaTestUtils.consumerProps("dlt-test-group", "false", embeddedKafkaBroker);

        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(Message.class)
        );
    }

    @Test
    void testMessageGoesToDLT_AfterRetries() {

        // GIVEN
        Message message = new Message(
                UUID.randomUUID().toString(),
                "ALWAYS_FAIL - This should go to DLT",
                LocalDateTime.now()
        );

        var consumer = dltConsumerFactory().createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, DLT_TOPIC);

        // WHEN
        kafkaTemplate.send(MAIN_TOPIC, message.getId(), message);

        // THEN
        var record = KafkaTestUtils.getSingleRecord(consumer, DLT_TOPIC);

        assertThat(record).isNotNull();
        assertThat(record.value().getContent()).contains("ALWAYS_FAIL");
    }
}
