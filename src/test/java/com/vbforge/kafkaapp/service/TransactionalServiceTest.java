package com.vbforge.kafkaapp.service;

import com.vbforge.kafkaapp.metrics.KafkaMetricsService;
import com.vbforge.kafkaapp.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionalServiceTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private KafkaMetricsService metricsService;

    private TransactionalService transactionalService;

    @BeforeEach
    void setUp() {
        transactionalService = new TransactionalService(kafkaTemplate, metricsService);
        ReflectionTestUtils.setField(transactionalService, "transactionsTopic", "test-topic");
    }

    @Test
    void testSendSingleTransaction_Success() {
        // Given
        Transaction transaction = new Transaction(
                "tx-1", "DEBIT", "ACC-123", 100.0, LocalDateTime.now(), "Test"
        );

        // When
        String result = transactionalService.sendSingleTransaction(transaction);

        // Then
        assertThat(result).isEqualTo("COMMITTED");
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any(Transaction.class));
        verify(metricsService, times(1)).incrementMessagesSent();
    }

    @Test
    void testSendBatchTransactions_AllSuccess() throws InterruptedException {
        // Given
        List<Transaction> transactions = List.of(
                new Transaction("tx-1", "DEBIT", "ACC-1", 100.0, LocalDateTime.now(), "Test 1"),
                new Transaction("tx-2", "CREDIT", "ACC-2", 200.0, LocalDateTime.now(), "Test 2")
        );

        // When
        String result = transactionalService.sendBatchTransactions(transactions);

        // Then
        assertThat(result).isEqualTo("COMMITTED");
        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), any(Transaction.class));
        verify(metricsService, times(2)).incrementMessagesSent();
    }

    @Test
    void testSendBatchTransactions_WithFailure() {
        // Given
        List<Transaction> transactions = List.of(
                new Transaction("tx-1", "DEBIT", "ACC-1", 100.0, LocalDateTime.now(), "Test 1"),
                new Transaction("tx-2", "ERROR", "ACC-2", 200.0, LocalDateTime.now(), "Will fail")
        );

        // When/Then
        assertThatThrownBy(() -> transactionalService.sendBatchTransactions(transactions))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Transaction failed and rolled back");

        verify(metricsService, times(1)).incrementMessagesFailed();
    }


}