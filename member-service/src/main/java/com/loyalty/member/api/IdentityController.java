package com.loyalty.member.api;

import com.loyalty.common.context.ContextHolder;
import com.loyalty.common.context.TenantContext;
import com.loyalty.member.api.dto.MemberDtos;
import com.loyalty.member.identity.IdentityService;
import com.loyalty.member.identity.domain.MemberIdentity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.loyalty.member.api.dto.MemberDtos.*;

/** Identity API (design 16.4–16.6 / 24.8): bind, replace, revoke, list. */
@RestController
@RequestMapping("/api/v1/programs/{programId}/members/{memberId}/identities")
public class IdentityController {

    private final IdentityService identityService;

    public IdentityController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping
    public List<IdentityResponse> list(@PathVariable UUID programId, @PathVariable UUID memberId) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        return identityService.list(ctx.tenantId(), programId, memberId).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    public IdentityResponse bind(@PathVariable UUID programId, @PathVariable UUID memberId,
                                 @RequestBody IdentityRequest req) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        MemberIdentity identity = identityService.bind(ctx.tenantId(), programId, memberId, req);
        return toResponse(identity);
    }

    @PostMapping("{identityId}/replace")
    public IdentityResponse replace(@PathVariable UUID programId, @PathVariable UUID memberId,
                                    @PathVariable UUID identityId, @RequestBody IdentityRequest req) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        MemberIdentity identity = identityService.replace(ctx.tenantId(), programId, memberId, identityId, req);
        return toResponse(identity);
    }

    @PostMapping("{identityId}/revoke")
    public void revoke(@PathVariable UUID programId, @PathVariable UUID memberId,
                       @PathVariable UUID identityId) {
        identityService.revoke(programId, memberId, identityId);
    }

    private IdentityResponse toResponse(MemberIdentity i) {
        return new IdentityResponse(i.id(), i.memberId(), i.identityType(), i.identitySource(),
                i.identityValue(), i.verified(), i.isPrimary(), i.status().name(),
                i.effectiveFrom(), i.effectiveTo());
    }
}
