package com.loyalty.engine.point;

import com.loyalty.common.event.EventEnvelope;
import com.loyalty.common.event.InboxProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Point-event projection consumer (design 20.3 / M5-T02). Demonstrates the Inbox pattern:
 * the same {@code loyalty.point.earned.v1} event is idempotently processed once via
 * (consumer_name, event_id). Tier/Benefit services add their own consumers on the same
 * topic with distinct consumer_name (design: same event consumed by multiple services).
 */
@Service
public class PointEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PointEventConsumer.class);
    public static final String CONSUMER_NAME = "engine-point-projection";

    private final InboxProcessor inbox;
    private final ObjectMapper json;

    public PointEventConsumer(InboxProcessor inbox, ObjectMapper json) {
        this.inbox = inbox;
        this.json = json;
    }

    @KafkaListener(topics = "loyalty.point.events", groupId = CONSUMER_NAME)
    public void on(String payload) {
        try {
            EventEnvelope envelope = json.readValue(payload, EventEnvelope.class);
            if (inbox.begin(CONSUMER_NAME, envelope)) {
                // Projection/audit effect only for M5. Tier-trigger and benefit-grant land in M7.
                log.debug("point event projected: type={} subject={}", envelope.type(), envelope.subject());
                inbox.complete(CONSUMER_NAME, envelope.id());
            }
        } catch (Exception ex) {
            log.warn("point event consume failed: {}", ex.toString());
            throw new IllegalStateException(ex);
        }
    }
}
