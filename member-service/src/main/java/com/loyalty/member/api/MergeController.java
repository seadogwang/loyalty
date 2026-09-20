package com.loyalty.member.api;

import com.loyalty.common.context.ContextHolder;
import com.loyalty.common.context.PrincipalContext;
import com.loyalty.member.api.dto.MergeDtos;
import com.loyalty.member.merge.MergeService;
import com.loyalty.member.member.domain.Member;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.loyalty.member.api.dto.MergeDtos.*;

/** Member merge API (design 17 / 24.8). */
@RestController
@RequestMapping("/api/v1/programs/{programId}/members/{sourceMemberId}")
public class MergeController {

    private final MergeService mergeService;

    public MergeController(MergeService mergeService) {
        this.mergeService = mergeService;
    }

    @PostMapping("/merge")
    public MergeResponse merge(@PathVariable UUID programId, @PathVariable UUID sourceMemberId,
                              @RequestBody MergeRequest req) {
        PrincipalContext principal = ContextHolder.principalContext();
        String operator = principal != null ? (principal.principalId() != null
                ? principal.principalId().toString() : principal.subject()) : null;
        Member source = mergeService.merge(programId, sourceMemberId, req.targetMemberId(), req.reason(), operator);
        return new MergeResponse(sourceMemberId, req.targetMemberId(), source.status().name());
    }
}
