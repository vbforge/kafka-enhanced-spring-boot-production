package com.vbforge.kafkaapp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Order {

    private String orderId;
    private String customerId;
    private String product;
    private Double amount;
    private LocalDateTime orderDate;

}
