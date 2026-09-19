package com.loyalty.engine.account;

import com.loyalty.common.context.ContextHolder;
import com.loyalty.common.context.TenantContext;
import com.loyalty.common.enums.AccountStatus;
import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import com.loyalty.engine.account.api.dto.AccountDtos;
import com.loyalty.engine.account.domain.Account;
import com.loyalty.engine.account.infrastructure.AccountMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Account lifecycle (design M3-T04). An account belongs to a member within a program;
 * the member must exist in the caller's tenant scope. ACTIVE/SUSPENDED/CLOSED transitions
 * are enforced; SUSPENDED/CLOSED accounts reject point writes (checked by the point runtime).
 */
@Service
public class AccountService {

    private final AccountMapper accountMapper;

    public AccountService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    @Transactional
    public Account create(UUID programId, UUID memberId, AccountDtos.CreateAccountRequest req) {
        if (req.accountNo() == null || req.accountNo().isBlank() || req.accountType() == null || req.accountType().isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "accountNo and accountType are required");
        }
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        if (!accountMapper.memberExistsScoped(ctx.tenantId(), programId, memberId)) {
            throw new ApiException(ErrorCode.MEMBER_NOT_FOUND, "member not found in scope");
        }
        UUID id = UUID.randomUUID();
        try {
            accountMapper.insert(id, ctx.tenantId(), programId, memberId, req.accountNo(), req.accountType());
        } catch (DuplicateKeyException ex) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "account_no already exists in program");
        }
        return accountMapper.findByIdScoped(ctx.tenantId(), programId, memberId, id);
    }

    public Account get(UUID programId, UUID memberId, UUID accountId) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        Account a = accountMapper.findByIdScoped(ctx.tenantId(), programId, memberId, accountId);
        if (a == null) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found");
        }
        return a;
    }

    @Transactional
    public Account suspend(UUID programId, UUID memberId, UUID accountId) {
        Account a = get(programId, memberId, accountId);
        if (a.status() != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "only ACTIVE accounts can be suspended");
        }
        accountMapper.setStatus(accountId, AccountStatus.SUSPENDED.name(), false);
        return get(programId, memberId, accountId);
    }

    @Transactional
    public Account close(UUID programId, UUID memberId, UUID accountId) {
        Account a = get(programId, memberId, accountId);
        if (a.status() == AccountStatus.CLOSED) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "account already closed");
        }
        accountMapper.setStatus(accountId, AccountStatus.CLOSED.name(), true);
        return get(programId, memberId, accountId);
    }
}
