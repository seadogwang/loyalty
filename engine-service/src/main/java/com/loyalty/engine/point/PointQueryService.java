package com.loyalty.engine.point;

import com.loyalty.common.context.ContextHolder;
import com.loyalty.common.context.TenantContext;
import com.loyalty.common.web.CursorPage;
import com.loyalty.common.web.CursorRequest;
import com.loyalty.engine.point.api.dto.PointDtos;
import com.loyalty.engine.point.domain.PointLedger;
import com.loyalty.engine.point.infrastructure.PointLedgerMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Point ledger query (design 24.6) — cursor-paginated, scope-aware. The cursor is an
 * opaque base64 of {@code epochMilli|uuid} referencing the last returned row.
 */
@Service
public class PointQueryService {

    private final PointLedgerMapper ledgerMapper;

    public PointQueryService(PointLedgerMapper ledgerMapper) {
        this.ledgerMapper = ledgerMapper;
    }

    public CursorPage<PointDtos.LedgerItem> ledger(UUID programId, UUID accountId, UUID pointTypeId,
                                                    String fromIso, String cursor, Integer limit) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        CursorRequest page = CursorRequest.of(cursor, limit);
        Instant from = fromIso == null || fromIso.isBlank() ? null : Instant.parse(fromIso);
        Instant cursorTime = null;
        UUID cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = decoded.indexOf('|');
            cursorTime = Instant.ofEpochMilli(Long.parseLong(decoded.substring(0, sep)));
            cursorId = UUID.fromString(decoded.substring(sep + 1));
        }
        List<PointLedger> rows = ledgerMapper.findPage(ctx.tenantId(), programId, accountId, pointTypeId,
                from, cursorTime, cursorId, page.limit() + 1);
        String nextCursor = null;
        if (rows.size() > page.limit()) {
            rows = rows.subList(0, page.limit());
            PointLedger last = rows.get(rows.size() - 1);
            nextCursor = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((last.createdAt().toEpochMilli() + "|" + last.id()).getBytes(StandardCharsets.UTF_8));
        }
        List<PointDtos.LedgerItem> items = rows.stream().map(PointQueryService::toItem).toList();
        return CursorPage.of(items, nextCursor);
    }

    private static PointDtos.LedgerItem toItem(PointLedger l) {
        return new PointDtos.LedgerItem(l.id(), l.transactionType().name(), l.amount(),
                l.effectiveAt(),
                l.sourceType() != null ? l.sourceType().name() : null,
                l.sourceId(), l.createdAt());
    }
}
