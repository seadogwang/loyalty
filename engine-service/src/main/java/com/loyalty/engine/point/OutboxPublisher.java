package com.loyalty.engine.point;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.engine.point.domain.OutboxEvent;
import com.loyalty.engine.point.infrastructure.OutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox publisher (design 21 / M5-T01). Drains NEW {@code outbox_event} rows with
 * FOR UPDATE SKIP LOCKED (multi-instance safe), publishes the CloudEvents envelope to
 * the topic derived from the event type, and marks PUBLISHED within the same tx.
 * Kafka delivery is at-least-once; consumers must be idempotent via the Inbox.
 *
 * <p>Topic mapping: {@code loyalty.point.earned.v1} -> {@code loyalty.point.events};
 * the Kafka key is the envelope subject (per-account / per-member ordering).
 */
@Service
@ConditionalOnProperty(prefix = "loyalty.outbox", name = "enabled", havingValue = "true")
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxMapper outboxMapper;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;

    @Value("${loyalty.outbox.batch-size:50}")
    private int batchSize;

    public OutboxPublisher(OutboxMapper outboxMapper, KafkaTemplate<String, String> kafka, ObjectMapper json) {
        this.outboxMapper = outboxMapper;
        this.kafka = kafka;
        this.json = json;
    }

    @Scheduled(fixedDelayString = "${loyalty.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxMapper.findNextToPublish(batchSize);
        if (batch.isEmpty()) return;
        for (OutboxEvent e : batch) {
            try {
                JsonNode node = json.readTree(e.payload());
                String topic = topicFor(node.path("type").asText());
                String key = node.path("subject").asText();
                kafka.send(topic, key, e.payload()).get();
                outboxMapper.markPublished(e.id());
            } catch (Exception ex) {
                log.warn("outbox publish failed for event {}: {}", e.id(), ex.toString());
                outboxMapper.markFailed(e.id(), ex.toString());
            }
        }
    }

    /** Map a CloudEvents type to its topic. */
    static String topicFor(String type) {
        if (type == null || type.isBlank()) return "loyalty.events";
        String[] parts = type.split("\\.");
        if (parts.length >= 2 && parts[0].equals("loyalty")) {
            return "loyalty." + parts[1] + ".events";
        }
        return "loyalty.events";
    }
}
