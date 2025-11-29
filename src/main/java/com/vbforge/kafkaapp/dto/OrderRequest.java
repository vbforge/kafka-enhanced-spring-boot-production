package com.vbforge.kafkaapp.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    @NotBlank(message = "Customer ID is required")
    @Size(min = 3, max = 50, message = "Customer ID must be between 3 and 50 characters")
    private String customerId;

    @NotBlank(message = "Product is required")
    @Size(min = 2, max = 100, message = "Product name must be between 2 and 100 characters")
    private String product;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "999999.99", message = "Amount cannot exceed 999,999.99")
    private Double amount;

    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 1000, message = "Quantity cannot exceed 1000")
    private Integer quantity;
}