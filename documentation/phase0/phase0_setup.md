# Phase 0: Project Setup & Kafka KRaft 🚀

## ✅ Phase 0 Checklist
- [ ] Create Spring Boot project
- [ ] Configure pom.xml
- [ ] Start Kafka with KRaft (no Zookeeper!)
- [ ] Create test topic
- [ ] Build simple producer/consumer
- [ ] Test with Postman

---

## Step 1: Create Spring Boot Project Structure

### 1.1 Project Structure
```
kafka-enhanced-spring-boot-production/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── vbforge/
│   │   │           └── kafkaapp/
│   │   │               ├── KafkaEnhancedApplication.java
│   │   │               ├── config/
│   │   │               ├── controller/
│   │   │               ├── consumer/
│   │   │               └── model/
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/
│           └── com/
│               └── vbforge/
│                   └── kafkaapp/
├── pom.xml
└── README.md
```

---

## Step 2: Complete pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" 
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.7</version>
        <relativePath/>
    </parent>

    <groupId>com.vbforge</groupId>
    <artifactId>kafka-enhanced-spring-boot-production</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>kafka-enhanced-spring-boot-production</name>
    <description>Production-ready Kafka application with Spring Boot</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Kafka -->
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring Boot Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Spring Kafka Test -->
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>

</project>
```

---

## Step 3: Start Kafka with KRaft (No Zookeeper!)

### 3.1 Open Command Prompt (Windows)
Open **Command Prompt as Administrator** or **PowerShell**

### 3.2 Navigate to Kafka Directory
```bash
cd C:\Soft\develop\kafka_2.13-4.1.0
```

### 3.3 Generate Cluster ID (First Time Only)
```bash
bin\windows\kafka-storage.bat random-uuid
```

**Example output:**
```
J7s9-xIYSdepTFqbJuEAmg
```

**⚠️ IMPORTANT: Copy this UUID! You'll need it in the next step.**

### 3.4 Format Log Directories (First Time Only)
Replace `YOUR_UUID` with the UUID from previous step:

```bash
bin\windows\kafka-storage.bat format -t YOUR_UUID -c config\server.properties
```

**Example:**
```bash
bin\windows\kafka-storage.bat format -t J7s9-xIYSdepTFqbJuEAmg -c config\server.properties
```

**Expected output:**
```
Formatting /tmp/kraft-combined-logs with metadata.version 3.5-IV2.
```

### 3.5 Start Kafka Server (KRaft Mode)
```bash
.\bin\windows\kafka-server-start.bat config\server.properties
```

**✅ Success indicators:**
```
[KafkaServer id=1] started (kafka.server.KafkaServer)
[BrokerToControllerChannelManager broker=1 name=heartbeat]: Recorded new controller, from now on will use broker localhost:9092
```

**Keep this terminal open!** Kafka is now running.

---

## Step 4: Create Test Topic

### 4.1 Open New Command Prompt
Keep the Kafka server running in the first terminal.

### 4.2 Navigate to Kafka Directory
```bash
cd C:\Soft\develop\kafka_2.13-4.1.0
```

### 4.3 Create Topic
```bash
.\bin\windows\kafka-topics.bat --create --topic test-topic --bootstrap-server localhost:9092 --partitions 2 --replication-factor 1
```

**Expected output:**
```
Created topic test-topic.
```

### 4.4 Verify Topic Creation
```bash
.\bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092
```

**Expected output:**
```
test-topic
```

### 4.5 Describe Topic (Check Details)
```bash
.\bin\windows\kafka-topics.bat --describe --topic test-topic --bootstrap-server localhost:9092
```

**Expected output:**
```
Topic: test-topic	TopicId: xyz	PartitionCount: 2	ReplicationFactor: 1
	Topic: test-topic	Partition: 0	Leader: 1	Replicas: 1	Isr: 1
	Topic: test-topic	Partition: 1	Leader: 1	Replicas: 1	Isr: 1
```

---

## Step 5: Create Application Files

### 5.1 Main Application Class
**File**: `src/main/java/com/vbforge/kafkaapp/KafkaEnhancedApplication.java`

```java
package com.vbforge.kafkaapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KafkaEnhancedApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaEnhancedApplication.class, args);
    }
}
```

### 5.2 Application Properties
**File**: `src/main/resources/application.properties`

```properties
# Application Name
spring.application.name=kafka-enhanced-spring-boot-production

# Server Port
server.port=8080

# Kafka Configuration
spring.kafka.bootstrap-servers=localhost:9092

# Producer Configuration
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer

# Consumer Configuration
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=*
spring.kafka.consumer.auto-offset-reset=earliest

# Topic Names
kafka.topic.test=test-topic

# Logging
logging.level.com.vbforge.kafkaapp=DEBUG
logging.level.org.apache.kafka=INFO
```

### 5.3 Message Model
**File**: `src/main/java/com/vbforge/kafkaapp/model/Message.java`

```java
package com.vbforge.kafkaapp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private String id;
    private String content;
    private LocalDateTime timestamp;
}
```

### 5.4 Kafka Configuration
**File**: `src/main/java/com/vbforge/kafkaapp/config/KafkaConfig.java`

```java
package com.vbforge.kafkaapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@Configuration
public class KafkaConfig {
    // Basic configuration - we'll add more in later phases
}
```

### 5.5 Simple Producer Controller
**File**: `src/main/java/com/vbforge/kafkaapp/controller/ProducerController.java`

```java
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
@RequestMapping("/api/producer")
@RequiredArgsConstructor
public class ProducerController {

    private final KafkaTemplate<String, Message> kafkaTemplate;

    @Value("${kafka.topic.test}")
    private String testTopic;

    @PostMapping("/send")
    public ResponseEntity<Message> sendMessage(@RequestParam(required = false) String content) {
        
        if (content == null || content.isEmpty()) {
            content = "Hello from Kafka!";
        }

        Message message = new Message(
            UUID.randomUUID().toString(),
            content,
            LocalDateTime.now()
        );

        kafkaTemplate.send(testTopic, message.getId(), message);
        
        log.info("Message sent: {}", message);
        
        return ResponseEntity.ok(message);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Producer is running!");
    }
}
```

### 5.6 Simple Consumer
**File**: `src/main/java/com/vbforge/kafkaapp/consumer/SimpleConsumer.java`

```java
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
    public void consume(Message message) {
        log.info("==================================================");
        log.info("Received message:");
        log.info("  ID: {}", message.getId());
        log.info("  Content: {}", message.getContent());
        log.info("  Timestamp: {}", message.getTimestamp());
        log.info("==================================================");
    }
}
```

---

## Step 6: Build and Run Application

### 6.1 Build the Project
```bash
mvn clean install
```

**Expected output:**
```
[INFO] BUILD SUCCESS
```

### 6.2 Run the Application
```bash
mvn spring-boot:run
```

**✅ Success indicators:**
```
Started KafkaEnhancedApplication in X.XXX seconds
Kafka version: 3.x.x
```

---

## Step 7: Test with Postman

### 7.1 Create Postman Collection
Create new collection: **"Kafka Enhanced - Phase 0"**

### 7.2 Test Health Endpoint
```
GET http://localhost:8080/api/producer/health
```

**Expected Response:**
```
Producer is running!
```

### 7.3 Send Simple Message
```
POST http://localhost:8080/api/producer/send
```

**Expected Response:**
```json
{
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "content": "Hello from Kafka!",
    "timestamp": "2025-11-16T10:30:00"
}
```

### 7.4 Send Custom Message
```
POST http://localhost:8080/api/producer/send?content=My first Kafka message!
```

**Expected Response:**
```json
{
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "content": "My first Kafka message!",
    "timestamp": "2025-11-16T10:31:00"
}
```

### 7.5 Check Console Logs
You should see in your application console:

```
Message sent: Message(id=550e8400-..., content=Hello from Kafka!, ...)
==================================================
Received message:
  ID: 550e8400-e29b-41d4-a716-446655440000
  Content: Hello from Kafka!
  Timestamp: 2025-11-16T10:30:00
==================================================
```

---

## Step 8: Verify Everything Works

### 8.1 Kafka Command Line Consumer (Optional)
Open another terminal and run:

```bash
cd C:\Soft\develop\kafka_2.13-4.1.0
.\bin\windows\kafka-console-consumer.bat --topic test-topic --from-beginning --bootstrap-server localhost:9092
```

Send a message via Postman and you'll see it in the console!

### 8.2 Check Consumer Group
```bash
.\bin\windows\kafka-consumer-groups.bat --bootstrap-server localhost:9092 --describe --group test-consumer-group
```

**Expected output:**
```
GROUP              TOPIC           PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
test-consumer-group test-topic      0          5               5               0
test-consumer-group test-topic      1          3               3               0
```

---

## ✅ Phase 0 Completion Checklist

Check off each item:

- [ok] Kafka started with KRaft (no Zookeeper)
- [ok] Topic `test-topic` created with 2 partitions
- [ok] Spring Boot application runs without errors
- [ok] Health endpoint returns "Producer is running!"
- [ok] Message sent via Postman
- [ok] Consumer receives and logs the message
- [ok] No errors in console

---

## 🎉 Congratulations!

You've completed **Phase 0**! Here's what you achieved:

✅ Set up Kafka with KRaft mode (modern approach)  
✅ Created Spring Boot project structure  
✅ Built simple producer and consumer  
✅ Tested end-to-end message flow  
✅ Ready for Phase 1!

---

## 🐛 Troubleshooting

### Issue: Kafka won't start
**Solution**: Make sure no other Kafka instance is running on port 9092
```bash
netstat -ano | findstr :9092
```
Kill the process if found.

### Issue: "Topic already exists"
**Solution**: Delete and recreate:
```bash
bin\windows\kafka-topics.bat --delete --topic test-topic --bootstrap-server localhost:9092
```

### Issue: Application can't connect to Kafka
**Solution**: Check if Kafka is running:
```bash
.\bin\windows\kafka-broker-api-versions.bat --bootstrap-server localhost:9092
```

### Issue: Consumer not receiving messages
**Solution**: Check consumer group lag:
```bash
.\bin\windows\kafka-consumer-groups.bat --bootstrap-server localhost:9092 --describe --group test-consumer-group
```

---

## 📸 Verification Screenshots Checklist

Take screenshots of:
1. Kafka server started (showing KRaft mode)
2. Topic created successfully
3. Spring Boot application started
4. Postman request + response
5. Console showing producer and consumer logs

---

## 🚀 Ready for Phase 1?

**When you're ready, reply with:**
- "Phase 0 complete! Ready for Phase 1!"
- Share any issues you encountered
- Screenshots (optional but helpful!)

**Next up**: Phase 1 - Producer Patterns (Sync, Async, with Keys) 📤

---

## 📝 Quick Command Reference

```bash
# Start Kafka (KRaft)
bin\windows\kafka-server-start.bat config\kraft\server.properties

# Create Topic
bin\windows\kafka-topics.bat --create --topic TOPIC_NAME --bootstrap-server localhost:9092 --partitions 2 --replication-factor 1

# List Topics
bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092

# Describe Topic
bin\windows\kafka-topics.bat --describe --topic TOPIC_NAME --bootstrap-server localhost:9092

# Console Consumer
bin\windows\kafka-console-consumer.bat --topic TOPIC_NAME --from-beginning --bootstrap-server localhost:9092

# Consumer Groups
bin\windows\kafka-consumer-groups.bat --bootstrap-server localhost:9092 --list
bin\windows\kafka-consumer-groups.bat --bootstrap-server localhost:9092 --describe --group GROUP_NAME

# Stop Kafka
Ctrl + C in the terminal running Kafka
```

Save this for quick reference! 📌