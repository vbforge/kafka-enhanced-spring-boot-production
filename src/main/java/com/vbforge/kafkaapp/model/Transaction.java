package com.vbforge.kafkaapp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    private String transactionId;
    private String type; // DEBIT, CREDIT, TRANSFER
    private String accountId;
    private Double amount;
    private LocalDateTime timestamp;
    private String description;
}