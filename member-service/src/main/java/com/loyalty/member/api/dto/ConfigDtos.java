package com.loyalty.member.api.dto;

import com.loyalty.common.enums.ConsumptionPolicy;

import java.util.UUID;

public final class ConfigDtos {
    private ConfigDtos() {}

    public record CreatePointTypeRequest(String code, String name,
                                          Boolean redeemable, Boolean tierCalculable, Boolean recordOnly,
                                          ConsumptionPolicy consumptionPolicy,
                                          String validityType, Integer validityPeriod) {}

    public record PointTypeResponse(UUID id, String code, String name,
                                     boolean redeemable, boolean tierCalculable, boolean recordOnly,
                                     String consumptionPolicy, String validityType, Integer validityPeriod,
                                     String status) {}

    public record ProgramResponse(UUID id, String code, String name, String status, String timezone) {}
}
