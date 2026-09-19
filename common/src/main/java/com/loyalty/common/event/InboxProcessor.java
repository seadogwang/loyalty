package com.loyalty.common.event;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Inbox idempotency facade (design 21 / M5-T02). A consumer calls {@link #begin} to
 * record receipt (idempotent on consumer+event); if it returns true, the consumer
 * performs its local business write and calls {@link #complete} (or {@link #fail}) within
 * the same transaction. Re-delivery of a processed event is a no-op.
 */
@Service
public class InboxProcessor {

    private final InboxMapper inboxMapper;

    public InboxProcessor(InboxMapper inboxMapper) {
        this.inboxMapper = inboxMapper;
    }

    @Transactional
    public boolean begin(String consumerName, EventEnvelope envelope) {
        return inboxMapper.insertReceived(consumerName, envelope.id(),
                envelope.tenantId(), envelope.programId(), envelope.type()) == 1;
    }

    @Transactional
    public void complete(String consumerName, UUID eventId) {
        inboxMapper.markProcessed(consumerName, eventId);
    }

    @Transactional
    public void fail(String consumerName, UUID eventId, String error) {
        inboxMapper.markFailed(consumerName, eventId, error);
    }
}
