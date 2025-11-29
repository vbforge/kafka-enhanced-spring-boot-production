package com.vbforge.kafkaapp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Message {

    private String id;
    private String content;
    private LocalDateTime timestamp;

}
