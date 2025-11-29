package com.vbforge.kafkaapp.integration;

import com.vbforge.kafkaapp.model.Transaction;
import com.vbforge.kafkaapp.service.TransactionalService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Key features:
 *  - Unique IDs: Each test creates transactions with unique IDs
 *  - Filtering: Tests search for only THEIR transactions
 *  - Ordered Execution: @Order ensures predictable test sequence
 *  - Polling Strategy: Uses getRecords() and filters results
 * */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = { "transaction-test-topic" },
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:0",
                "auto.create.topics.enable=true",
                "transaction.state.log.replication.factor=1",
                "transaction.state.log.min.isr=1"
        }
)
@TestPropertySource(properties = {
        "kafka.topic.transactions=transaction-test-topic",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.producer.transaction-id-prefix=test-tx-"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransactionIntegrationTest {

    private static final String TOPIC = "transaction-test-topic";

    @Autowired
    private TransactionalService transactionalService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker; //red line because it will explore in runtime

    private Consumer<String, Transaction> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> props =
                KafkaTestUtils.consumerProps(
                        "tx-test-group-" + UUID.randomUUID(),
                        "false",
                        embeddedKafkaBroker
                );

        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Transaction.class);

        DefaultKafkaConsumerFactory<String, Transaction> consumerFactory =
                new DefaultKafkaConsumerFactory<>(
                        props,
                        new StringDeserializer(),
                        new JsonDeserializer<>(Transaction.class)
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
    @Order(1)
    void testSingleTransactionCommit() throws Exception {
        // GIVEN
        String uniqueTxId = "tx-single-" + UUID.randomUUID();
        Transaction transaction = new Transaction(
                uniqueTxId,
                "DEBIT",
                "ACC-100",
                500.0,
                LocalDateTime.now(),
                "Single transaction test"
        );

        // WHEN
        String result = transactionalService.sendSingleTransaction(transaction);

        // THEN
        assertThat(result).isEqualTo("COMMITTED");

        // Wait for the specific transaction
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ConsumerRecords<String, Transaction> records =
                    KafkaTestUtils.getRecords(consumer, Duration.ofMillis(1000));

            List<Transaction> allTransactions = new java.util.ArrayList<>();
            records.forEach(record -> allTransactions.add(record.value()));

            // Find our specific transaction
            Transaction ourTransaction = allTransactions.stream()
                    .filter(tx -> uniqueTxId.equals(tx.getTransactionId()))
                    .findFirst()
                    .orElse(null);

            assertThat(ourTransaction)
                    .withFailMessage("Transaction " + uniqueTxId + " not found. " +
                            "Found: " + allTransactions.size() + " transactions")
                    .isNotNull();

            assertThat(ourTransaction.getAmount()).isEqualTo(500.0);
            assertThat(ourTransaction.getType()).isEqualTo("DEBIT");
            assertThat(ourTransaction.getAccountId()).isEqualTo("ACC-100");
        });
    }

    @Test
    @Order(2)
    void testTransactionCommit_AllMessagesReceived() throws Exception {
        // GIVEN
        String txId1 = "tx-batch-1-" + UUID.randomUUID();
        String txId2 = "tx-batch-2-" + UUID.randomUUID();

        List<Transaction> transactions = List.of(
                new Transaction(txId1, "DEBIT", "ACC-1", 100.0,
                        LocalDateTime.now(), "Test 1"),
                new Transaction(txId2, "CREDIT", "ACC-2", 200.0,
                        LocalDateTime.now(), "Test 2")
        );

        // WHEN
        String result = transactionalService.sendBatchTransactions(transactions);

        // THEN
        assertThat(result).isEqualTo("COMMITTED");

        // Wait for our specific transactions
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ConsumerRecords<String, Transaction> records =
                    KafkaTestUtils.getRecords(consumer, Duration.ofMillis(1000));

            List<Transaction> receivedTransactions = new java.util.ArrayList<>();
            records.forEach(record -> receivedTransactions.add(record.value()));

            // Filter for our transactions
            List<String> receivedIds = receivedTransactions.stream()
                    .map(Transaction::getTransactionId)
                    .filter(id -> id.equals(txId1) || id.equals(txId2))
                    .toList();

            assertThat(receivedIds)
                    .withFailMessage("Expected transactions not found")
                    .containsExactlyInAnyOrder(txId1, txId2);
        });
    }

    @Test
    @Order(3)
    void testTransactionRollback_NoMessagesReceived() {
        // GIVEN
        String txId1 = "tx-rollback-1-" + UUID.randomUUID();
        String txId2 = "tx-rollback-2-" + UUID.randomUUID();

        List<Transaction> transactions = List.of(
                new Transaction(txId1, "DEBIT", "ACC-1", 100.0,
                        LocalDateTime.now(), "Test 1"),
                new Transaction(txId2, "ERROR", "ACC-2", 200.0,
                        LocalDateTime.now(), "Will trigger rollback")
        );

        // WHEN
        assertThatThrownBy(() -> transactionalService.sendBatchTransactions(transactions))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("rolled back");

        // THEN – our transactions should NOT be committed
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            ConsumerRecords<String, Transaction> records =
                    KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500));

            List<Transaction> receivedTransactions = new java.util.ArrayList<>();
            records.forEach(record -> receivedTransactions.add(record.value()));

            // Our rolled-back transactions should NOT be present
            boolean foundRolledBack = receivedTransactions.stream()
                    .anyMatch(tx -> txId1.equals(tx.getTransactionId()) ||
                            txId2.equals(tx.getTransactionId()));

            assertThat(foundRolledBack)
                    .withFailMessage("Rolled back transactions should not be found!")
                    .isFalse();
        });
    }
}
