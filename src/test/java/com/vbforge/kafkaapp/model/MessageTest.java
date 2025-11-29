package com.vbforge.kafkaapp.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
public class MessageTest {

    @Test
    public void testCreateMessageModel(){

        String id = "msg-123";
        String content = "Test Message";
        LocalDateTime timestamp = LocalDateTime.now();

        Message message = new Message(id, content, timestamp);

        assertThat(message.getId()).isEqualTo(id);
        assertThat(message.getContent()).isEqualTo(content);
        assertThat(message.getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    void testMessageEquality() {

        LocalDateTime now = LocalDateTime.now();
        Message message1 = new Message("1", "content", now);
        Message message2 = new Message("1", "content", now);

        assertThat(message1).isEqualTo(message2);
    }

    @Test
    void testMessageToString() {

        Message message = new Message("1", "Test", LocalDateTime.now());

        String toString = message.toString();

        assertThat(toString).contains("1", "Test");
    }

}
