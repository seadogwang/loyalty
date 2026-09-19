package com.loyalty.engine.point.domain;

import com.loyalty.common.enums.ConsumptionPolicy;

import java.util.UUID;

/** Read-only view of point_type fields the engine needs (design 4.8). */
public record PointTypeView(
        UUID id,
        boolean redeemable,
        boolean tierCalculable,
        boolean recordOnly,
        ConsumptionPolicy consumptionPolicy) {
}
