package com.loyalty.engine.point;

import com.loyalty.common.event.EventEnvelope;
import com.loyalty.engine.point.infrastructure.OutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Outbox publisher (design 21). Writes the CloudEvents envelope into {@code outbox_event}
 * within the same transaction as the business write; the Kafka publisher (M5) drains
 * NEW rows after commit.
 */
@Service
public class OutboxService {

    private final OutboxMapper outbox;
    private final ObjectMapper json;

    public OutboxService(OutboxMapper outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    public void publish(EventEnvelope envelope) {
        try {
            String payload = json.writeValueAsString(envelope);
            outbox.insert(UUID.randomUUID(),
                    envelope.tenantId(), envelope.programId(),
                    "point", envelope.subject(), envelope.type(), payload);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to write outbox event", ex);
        }
    }
}
