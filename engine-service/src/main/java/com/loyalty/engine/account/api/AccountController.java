package com.loyalty.engine.account.api;

import com.loyalty.engine.account.AccountService;
import com.loyalty.engine.account.api.dto.AccountDtos;
import com.loyalty.engine.account.domain.Account;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.loyalty.engine.account.api.dto.AccountDtos.*;

/** Account API (design M3-T04 / V014 mapping). */
@RestController
@RequestMapping("/api/v1/programs/{programId}/members/{memberId}/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public AccountResponse create(@PathVariable UUID programId, @PathVariable UUID memberId,
                                  @RequestBody CreateAccountRequest req) {
        Account a = accountService.create(programId, memberId, req);
        return toResponse(a);
    }

    @GetMapping("/{accountId}")
    public AccountResponse get(@PathVariable UUID programId, @PathVariable UUID memberId,
                              @PathVariable UUID accountId) {
        return toResponse(accountService.get(programId, memberId, accountId));
    }

    @PostMapping("/{accountId}/suspend")
    public AccountResponse suspend(@PathVariable UUID programId, @PathVariable UUID memberId,
                                   @PathVariable UUID accountId) {
        return toResponse(accountService.suspend(programId, memberId, accountId));
    }

    @PostMapping("/{accountId}/close")
    public AccountResponse close(@PathVariable UUID programId, @PathVariable UUID memberId,
                                 @PathVariable UUID accountId) {
        return toResponse(accountService.close(programId, memberId, accountId));
    }

    private static AccountResponse toResponse(Account a) {
        return new AccountResponse(a.id(), a.memberId(), a.accountNo(), a.accountType(),
                a.status().name(), a.openedAt(), a.closedAt());
    }
}
