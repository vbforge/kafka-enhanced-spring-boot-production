package com.vbforge.kafkaapp.consumer;

import com.vbforge.kafkaapp.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderConsumer {

    @KafkaListener(
            topics = "${kafka.topic.orders}",
            groupId = "orders-consumer-group"
    )
    public void consumeOrder(
            ConsumerRecord<String, Order> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) int offset){

        Order order = record.value();
        String customerKey = record.key();

        log.info("════════════════════════════════════");
        log.info("🛒 ORDER RECEIVED");
        log.info("  Order ID: {}", order.getOrderId());
        log.info("  Customer ID (KEY): {}", customerKey);
        log.info("  Product: {}", order.getProduct());
        log.info("  Amount: ${}", order.getAmount());
        log.info("  Order Date: {}", order.getOrderDate());
        log.info("  ► Partition: {} (All orders for {} go here!)", partition, customerKey);
        log.info("  ► Offset: {}", offset);
        log.info("════════════════════════════════════");

    }

}
