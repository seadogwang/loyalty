package com.loyalty.member.config;

import com.loyalty.common.context.ContextHolder;
import com.loyalty.common.context.TenantContext;
import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import com.loyalty.member.api.dto.ConfigDtos;
import com.loyalty.member.config.domain.PointType;
import com.loyalty.member.config.infrastructure.PointTypeMapper;
import com.loyalty.member.config.infrastructure.ProgramMapper;
import com.loyalty.member.config.domain.Program;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Point-type configuration (design 4.8 / M3-T01). Enforces the capability invariant
 * {@code NOT (record_only AND redeemable)} (also a DB CHECK) and validates the program
 * belongs to the caller's tenant scope.
 */
@Service
public class PointTypeService {

    private final PointTypeMapper pointTypeMapper;
    private final ProgramMapper programMapper;

    public PointTypeService(PointTypeMapper pointTypeMapper, ProgramMapper programMapper) {
        this.pointTypeMapper = pointTypeMapper;
        this.programMapper = programMapper;
    }

    @Transactional
    public PointType create(UUID programId, ConfigDtos.CreatePointTypeRequest req) {
        if (req.code() == null || req.code().isBlank() || req.name() == null || req.name().isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "code and name are required");
        }
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        requireProgramInScope(ctx.tenantId(), programId);

        boolean redeemable = bool(req.redeemable());
        boolean tierCalculable = bool(req.tierCalculable());
        boolean recordOnly = bool(req.recordOnly());
        if (recordOnly && redeemable) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "record_only and redeemable are mutually exclusive");
        }
        var policy = req.consumptionPolicy() == null
                ? com.loyalty.common.enums.ConsumptionPolicy.FEFO : req.consumptionPolicy();
        UUID id = UUID.randomUUID();
        try {
            pointTypeMapper.insert(id, ctx.tenantId(), programId, req.code(), req.name(),
                    redeemable, tierCalculable, recordOnly, policy.name(),
                    req.validityType() == null ? "NEVER" : req.validityType(), req.validityPeriod());
        } catch (DuplicateKeyException ex) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "point type code already exists in program");
        }
        return pointTypeMapper.findByIdScoped(ctx.tenantId(), programId, id);
    }

    public List<PointType> list(UUID programId) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        return pointTypeMapper.listByProgram(ctx.tenantId(), programId);
    }

    public PointType getByCode(UUID programId, String code) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        PointType pt = pointTypeMapper.findByCodeScoped(ctx.tenantId(), programId, code);
        if (pt == null) {
            throw new ApiException(ErrorCode.POINT_TYPE_NOT_FOUND, "point type not found");
        }
        return pt;
    }

    /** Validate the program belongs to the caller's tenant (defense-in-depth with RLS). */
    public Program requireProgramInScope(UUID tenantId, UUID programId) {
        Program p = programMapper.findScoped(tenantId, programId);
        if (p == null) {
            throw new ApiException(ErrorCode.TENANT_SCOPE_VIOLATION, "program not in caller tenant scope");
        }
        return p;
    }

    private static boolean bool(Boolean b) {
        return Boolean.TRUE.equals(b);
    }
}
