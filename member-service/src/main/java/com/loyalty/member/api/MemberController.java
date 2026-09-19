package com.loyalty.member.api;

import com.loyalty.member.api.dto.MemberDtos;
import com.loyalty.member.member.MemberService;
import com.loyalty.member.member.domain.Member;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.loyalty.member.api.dto.MemberDtos.*;

/** Member API (design 24.8 + M3-T02): create, get, resolve. */
@RestController
@RequestMapping("/api/v1/programs/{programId}/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping
    public MemberResponse create(@PathVariable UUID programId, @RequestBody CreateMemberRequest req) {
        Member m = memberService.createMember(programId, req.memberNo(), req.initialIdentity());
        return new MemberResponse(m.id(), m.memberNo(), m.status().name(), m.mergedToMemberId());
    }

    @GetMapping("/{memberId}")
    public MemberResponse get(@PathVariable UUID programId, @PathVariable UUID memberId) {
        Member m = memberService.getMember(programId, memberId);
        return new MemberResponse(m.id(), m.memberNo(), m.status().name(), m.mergedToMemberId());
    }

    @PostMapping("/resolve")
    public MemberResponse resolve(@PathVariable UUID programId, @RequestBody ResolveRequest req) {
        Member m = memberService.resolveMember(programId, req.identities());
        return new MemberResponse(m.id(), m.memberNo(), m.status().name(), m.mergedToMemberId());
    }
}
