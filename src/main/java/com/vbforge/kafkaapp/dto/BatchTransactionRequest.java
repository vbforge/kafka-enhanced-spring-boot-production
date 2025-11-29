package com.vbforge.kafkaapp.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchTransactionRequest {

    @NotEmpty(message = "Transaction list cannot be empty")
    @Size(min = 1, max = 10, message = "Batch size must be between 1 and 10")
    private List<TransactionItem> transactions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionItem {
        
        @NotBlank(message = "Account ID is required")
        private String accountId;
        
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be positive")
        private Double amount;
        
        @Pattern(regexp = "^(DEBIT|CREDIT|TRANSFER)$", 
                 message = "Type must be DEBIT, CREDIT, or TRANSFER")
        private String type;
        
        private String description;
    }
}