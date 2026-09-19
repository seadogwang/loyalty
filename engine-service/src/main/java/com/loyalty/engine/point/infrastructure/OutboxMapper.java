package com.loyalty.engine.point.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/** Outbox event DAO (design 21). Inserted in the same transaction as the business write;
 *  published to Kafka by the outbox publisher (M5). */
@Mapper
public interface OutboxMapper {

    int insert(@Param("id") UUID id,
               @Param("tenantId") UUID tenantId,
               @Param("programId") UUID programId,
               @Param("aggregateType") String aggregateType,
               @Param("aggregateId") String aggregateId,
               @Param("eventType") String eventType,
               @Param("payload") String payloadJson);
}
