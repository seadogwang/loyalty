package com.loyalty.common.event;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/**
 * Inbox idempotency DAO (design 21 / M5-T02). {@code (consumer_name, event_id)} is the
 * primary key; insertReceived is idempotent so a replayed event is processed once.
 */
@Mapper
public interface InboxMapper {

    /** Idempotent insert; returns 1 if this consumer+event is new, 0 if already seen. */
    int insertReceived(@Param("consumerName") String consumerName,
                       @Param("eventId") UUID eventId,
                       @Param("tenantId") UUID tenantId,
                       @Param("programId") UUID programId,
                       @Param("eventType") String eventType);

    int markProcessed(@Param("consumerName") String consumerName, @Param("eventId") UUID eventId);

    int markFailed(@Param("consumerName") String consumerName, @Param("eventId") UUID eventId,
                   @Param("error") String error);

    InboxEvent find(@Param("consumerName") String consumerName, @Param("eventId") UUID eventId);
}
