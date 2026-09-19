package com.loyalty.engine.point;

import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import com.loyalty.engine.point.domain.PointOperation;
import com.loyalty.engine.point.infrastructure.PointOperationMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Idempotency boundary for point operations (design 23.2). Lives inside the business
 * transaction: beginOperation inserts a PROCESSING row (or returns the prior result);
 * the caller completes/aborts within the same transaction.
 */
@Service
public class IdempotencyService {

    private final PointOperationMapper mapper;

    public IdempotencyService(PointOperationMapper mapper) {
        this.mapper = mapper;
    }

    public enum State { NEW, COMPLETED, CONFLICT, IN_PROGRESS }

    public record BeginResult(State state, PointOperation operation, String storedResponse) {}

    public BeginResult beginOperation(UUID tenantId, UUID programId, UUID memberId, UUID accountId,
                                      UUID pointTypeId, String operationType, String idempotencyKey,
                                      String requestHash, String actorId, String reasonCode,
                                      String correlationId) {
        UUID id = UUID.randomUUID();
        int inserted = mapper.insert(id, tenantId, programId, memberId, accountId, pointTypeId,
                operationType, idempotencyKey, requestHash, actorId, reasonCode, correlationId, "PROCESSING");
        if (inserted == 1) {
            PointOperation op = mapper.findByIdempotencyKey(tenantId, idempotencyKey);
            return new BeginResult(State.NEW, op, null);
        }
        // Existing operation under this idempotency key.
        PointOperation existing = mapper.findByIdempotencyKey(tenantId, idempotencyKey);
        if (existing == null) {
            // Rare race: insert returned 0 but row not visible yet; treat as in-progress.
            throw new ApiException(ErrorCode.OPERATION_IN_PROGRESS, "operation in progress");
        }
        if (!requestHash.equals(existing.requestHash())) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "idempotency key reused with a different request");
        }
        return switch (existing.status()) {
            case "COMPLETED" -> new BeginResult(State.COMPLETED, existing, existing.responseJson());
            case "PROCESSING" -> throw new ApiException(ErrorCode.OPERATION_IN_PROGRESS,
                    "operation already in progress");
            case "FAILED" -> {
                // Allow retry: reactivate the existing row and proceed.
                mapper.complete(existing.id(), "{}");
                yield new BeginResult(State.NEW, existing, null);
            }
            default -> throw new ApiException(ErrorCode.OPERATION_IN_PROGRESS,
                    "operation state: " + existing.status());
        };
    }

    public void complete(UUID operationId, String responseJson) {
        mapper.complete(operationId, responseJson);
    }

    public void fail(UUID operationId, String responseJson) {
        mapper.markFailed(operationId, responseJson);
    }
}
