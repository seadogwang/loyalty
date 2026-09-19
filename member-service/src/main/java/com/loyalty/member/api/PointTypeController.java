package com.loyalty.member.api;

import com.loyalty.common.context.ContextHolder;
import com.loyalty.common.context.TenantContext;
import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import com.loyalty.member.api.dto.ConfigDtos;
import com.loyalty.member.config.PointTypeService;
import com.loyalty.member.config.domain.PointType;
import com.loyalty.member.config.domain.Program;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.loyalty.member.api.dto.ConfigDtos.*;

/** Program + Point type config API (design M3-T01). */
@RestController
@RequestMapping("/api/v1/programs/{programId}")
public class PointTypeController {

    private final PointTypeService pointTypeService;

    public PointTypeController(PointTypeService pointTypeService) {
        this.pointTypeService = pointTypeService;
    }

    @GetMapping
    public ProgramResponse getProgram(@PathVariable UUID programId) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        Program p = pointTypeService.requireProgramInScope(ctx.tenantId(), programId);
        return new ProgramResponse(p.id(), p.code(), p.name(), p.status(), p.timezone());
    }

    @PostMapping("/point-types")
    public PointTypeResponse createPointType(@PathVariable UUID programId,
                                             @RequestBody CreatePointTypeRequest req) {
        PointType pt = pointTypeService.create(programId, req);
        return toResponse(pt);
    }

    @GetMapping("/point-types")
    public List<PointTypeResponse> listPointTypes(@PathVariable UUID programId) {
        return pointTypeService.list(programId).stream().map(PointTypeController::toResponse).toList();
    }

    @GetMapping("/point-types/{code}")
    public PointTypeResponse getPointType(@PathVariable UUID programId, @PathVariable String code) {
        return toResponse(pointTypeService.getByCode(programId, code));
    }

    private static PointTypeResponse toResponse(PointType pt) {
        return new PointTypeResponse(pt.id(), pt.code(), pt.name(), pt.redeemable(),
                pt.tierCalculable(), pt.recordOnly(), pt.consumptionPolicy().name(),
                pt.validityType(), pt.validityPeriod(), pt.status());
    }
}
