package com.vbforge.kafkaapp.consumer;

import com.vbforge.kafkaapp.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SimpleConsumer {

    @KafkaListener(
            topics = "${kafka.topic.test}",
            groupId = "test-consumer-group"
    )
    public void consume(Message message){
        log.info("===========================================");
        log.info("Received message:");
        log.info("  ID: {}", message.getId());
        log.info("  Content: {}", message.getContent());
        log.info("  Timestamp: {}", message.getTimestamp());
        log.info("===========================================");
    }


}
