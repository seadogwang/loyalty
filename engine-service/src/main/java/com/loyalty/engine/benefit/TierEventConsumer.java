package com.loyalty.engine.benefit;

import com.loyalty.common.event.EventEnvelope;
import com.loyalty.common.event.InboxProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Tier-event consumer (design 20.3 / M7-T03). On {@code loyalty.tier.changed.v1}, grants
 * the benefits mapped to the new tier. Idempotent via the Inbox
 * (consumer_name {@code engine-benefit}); a replayed event does not duplicate grants.
 */
@Service
public class TierEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TierEventConsumer.class);
    public static final String CONSUMER_NAME = "engine-benefit";

    private final InboxProcessor inbox;
    private final BenefitService benefitService;
    private final ObjectMapper json;

    public TierEventConsumer(InboxProcessor inbox, BenefitService benefitService, ObjectMapper json) {
        this.inbox = inbox;
        this.benefitService = benefitService;
        this.json = json;
    }

    @KafkaListener(topics = "loyalty.tier.events", groupId = CONSUMER_NAME)
    public void on(String payload) {
        try {
            EventEnvelope env = json.readValue(payload, EventEnvelope.class);
            if (!inbox.begin(CONSUMER_NAME, env)) {
                return; // already processed
            }
            JsonNode data = json.readTree(payload).path("data");
            UUID memberId = UUID.fromString(env.subject().substring(env.subject().indexOf('/') + 1));
            UUID newTierId = data.has("newTierId") && !data.get("newTierId").asText().isEmpty()
                    ? UUID.fromString(data.get("newTierId").asText()) : null;
            if (newTierId != null) {
                benefitService.grantForTier(env.tenantId(), env.programId(), memberId, newTierId,
                        "TIER", env.id().toString());
            }
            inbox.complete(CONSUMER_NAME, env.id());
        } catch (Exception ex) {
            log.warn("tier event consume failed: {}", ex.toString());
            throw new IllegalStateException(ex);
        }
    }
}
