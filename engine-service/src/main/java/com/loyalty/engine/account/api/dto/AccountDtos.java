package com.loyalty.engine.account.api.dto;

import java.util.UUID;

public final class AccountDtos {
    private AccountDtos() {}

    public record CreateAccountRequest(String accountNo, String accountType) {}

    public record AccountResponse(UUID id, UUID memberId, String accountNo, String accountType,
                                   String status, java.time.Instant openedAt, java.time.Instant closedAt) {}
}
