package com.vbforge.kafkaapp.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaHealthIndicator implements HealthIndicator {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public Health health() {
        try {
            // Try to get Kafka metrics (quick check)
            var metrics = kafkaTemplate.metrics();
            
            if (metrics != null && !metrics.isEmpty()) {
                return Health.up()
                    .withDetail("status", "Kafka is reachable")
                    .withDetail("broker", "localhost:9092")
                    .withDetail("metrics_count", metrics.size())
                    .build();
            } else {
                return Health.down()
                    .withDetail("status", "Kafka metrics not available")
                    .build();
            }
        } catch (Exception e) {
            log.error("❌ Kafka health check failed", e);
            return Health.down()
                .withDetail("status", "Kafka is NOT reachable")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}