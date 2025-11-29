package com.vbforge.kafkaapp.controller;

import com.vbforge.kafkaapp.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;


@Slf4j
@RestController
@RequestMapping("/api/demo/producer") //endpoints for postman with 'api/demo/producer'
@RequiredArgsConstructor
public class ProducerControllerDemo {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.test}")
    private String TEST_TOPIC;

    @PostMapping("/send")
    public ResponseEntity<Message> sendMessage(@RequestParam (required = false) String content){

        if(content == null || content.isEmpty()){
            content = "Hello from Kafka!";
        }

        Message message = new Message(
                UUID.randomUUID().toString(),
                content,
                LocalDateTime.now()
        );

        kafkaTemplate.send(TEST_TOPIC, message.getId(), message);

        log.info("Message sent: {}", message);

        return ResponseEntity.ok(message);
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck(){
        return ResponseEntity.ok("Producer is running!");
    }


}
