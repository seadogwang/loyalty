package com.loyalty.engine.point.infrastructure;

import com.loyalty.engine.point.domain.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/** Outbox event DAO (design 21). Inserted in the business tx; drained by the publisher. */
@Mapper
public interface OutboxMapper {

    int insert(@Param("id") UUID id,
               @Param("tenantId") UUID tenantId,
               @Param("programId") UUID programId,
               @Param("aggregateType") String aggregateType,
               @Param("aggregateId") String aggregateId,
               @Param("eventType") String eventType,
               @Param("payload") String payloadJson);

    /** Drain NEW rows for publication, locking them FOR UPDATE SKIP LOCKED so multiple
     *  instances do not grab the same rows. */
    List<OutboxEvent> findNextToPublish(@Param("limit") int limit);

    int markPublished(@Param("id") UUID id);

    int markFailed(@Param("id") UUID id, @Param("error") String error);
}
