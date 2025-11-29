package com.vbforge.kafkaapp.controller;

import com.vbforge.kafkaapp.dto.BatchTransactionRequest;
import com.vbforge.kafkaapp.model.Transaction;
import com.vbforge.kafkaapp.service.TransactionalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionalService transactionalService;

    /**
     * Send single transaction (always succeeds)
     */
    @PostMapping("/single")
    public ResponseEntity<Map<String, Object>> sendSingleTransaction(
            @RequestParam String accountId,
            @RequestParam Double amount,
            @RequestParam String type) {
        
        Transaction transaction = new Transaction(
            UUID.randomUUID().toString(),
            type,
            accountId,
            amount,
            LocalDateTime.now(),
            "Single transaction"
        );

        String result = transactionalService.sendSingleTransaction(transaction);

        Map<String, Object> response = new HashMap<>();
        response.put("transaction", transaction);
        response.put("status", result);
        response.put("message", "Transaction committed successfully");

        return ResponseEntity.ok(response);
    }

    /**
     * Send batch - ALL SUCCESS scenario
     * All transactions will be committed
     */
    @PostMapping("/batch-success")
    public ResponseEntity<Map<String, Object>> sendBatchSuccess() {
        
        List<Transaction> transactions = List.of(
            new Transaction(UUID.randomUUID().toString(), "DEBIT", "ACC-001", 100.0, 
                LocalDateTime.now(), "Payment 1"),
            new Transaction(UUID.randomUUID().toString(), "CREDIT", "ACC-002", 100.0, 
                LocalDateTime.now(), "Payment 2"),
            new Transaction(UUID.randomUUID().toString(), "TRANSFER", "ACC-003", 50.0, 
                LocalDateTime.now(), "Payment 3")
        );

        log.info("📤 Sending batch of {} transactions (ALL SUCCESS)", transactions.size());

        String result = transactionalService.sendBatchTransactions(transactions);

        Map<String, Object> response = new HashMap<>();
        response.put("transactions_count", transactions.size());
        response.put("status", result);
        response.put("message", "All transactions committed");
        response.put("transactions", transactions);

        return ResponseEntity.ok(response);
    }

    /**
     * Send batch - FAILURE scenario
     * One transaction will fail, causing ALL to rollback
     */
    @PostMapping("/batch-failure")
    public ResponseEntity<Map<String, Object>> sendBatchFailure() {
        
        List<Transaction> transactions = List.of(
            new Transaction(UUID.randomUUID().toString(), "DEBIT", "ACC-001", 100.0, 
                LocalDateTime.now(), "Payment 1 - Will succeed"),
            new Transaction(UUID.randomUUID().toString(), "ERROR", "ACC-002", 100.0, 
                LocalDateTime.now(), "Payment 2 - WILL FAIL"),
            new Transaction(UUID.randomUUID().toString(), "CREDIT", "ACC-003", 50.0, 
                LocalDateTime.now(), "Payment 3 - Never processed")
        );

        log.info("📤 Sending batch of {} transactions (WILL FAIL)", transactions.size());

        try {
            String result = transactionalService.sendBatchTransactions(transactions);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", result);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "ROLLED_BACK");
            response.put("message", "Transaction failed and rolled back");
            response.put("error", e.getMessage());
            response.put("transactions_attempted", transactions.size());
            response.put("transactions_committed", 0);
            response.put("note", "All transactions rolled back - NONE committed to Kafka");
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Custom batch with validation
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> sendCustomBatch(
            @Valid @RequestBody BatchTransactionRequest request) {
        
        List<Transaction> transactions = request.getTransactions().stream()
            .map(item -> new Transaction(
                UUID.randomUUID().toString(),
                item.getType(),
                item.getAccountId(),
                item.getAmount(),
                LocalDateTime.now(),
                item.getDescription()
            ))
            .collect(Collectors.toList());

        log.info("📤 Sending custom batch of {} transactions", transactions.size());

        try {
            String result = transactionalService.sendBatchTransactions(transactions);
            
            Map<String, Object> response = new HashMap<>();
            response.put("transactions_count", transactions.size());
            response.put("status", result);
            response.put("message", "All transactions committed");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "ROLLED_BACK");
            response.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Transaction API is running!");
    }
}