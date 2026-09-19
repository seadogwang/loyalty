package com.loyalty.engine.point;

import com.loyalty.common.enums.ConsumptionPolicy;
import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import com.loyalty.engine.point.domain.RedeemableAsset;
import com.loyalty.engine.point.domain.RedemptionAllocation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * FEFO / FIFO consumption allocator (design 4.9 / 10.6). Filters out expired / depleted
 * assets, sorts by the point-type policy, and consumes from each asset until the required
 * amount is met; throws {@code INSUFFICIENT_BALANCE} if the assets cannot cover it.
 */
@Service
public class AllocationPolicy {

    public List<RedemptionAllocation> allocate(List<RedeemableAsset> assets,
                                                ConsumptionPolicy policy,
                                                BigDecimal requiredAmount,
                                                Instant now) {
        List<RedeemableAsset> pool = new ArrayList<>();
        for (RedeemableAsset a : assets) {
            if (a.isExpired(now)) {
                continue;
            }
            if (a.remainingAmount().signum() <= 0) {
                continue;
            }
            pool.add(a);
        }
        pool.sort(comparator(policy));

        BigDecimal remaining = requiredAmount;
        List<RedemptionAllocation> result = new ArrayList<>();
        for (RedeemableAsset a : pool) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal consume = a.remainingAmount().min(remaining);
            result.add(new RedemptionAllocation(a.assetLedgerId(), consume));
            remaining = remaining.subtract(consume);
        }
        if (remaining.signum() > 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE, "insufficient redeemable balance");
        }
        return result;
    }

    private Comparator<RedeemableAsset> comparator(ConsumptionPolicy policy) {
        Comparator<RedeemableAsset> fefo = Comparator
                .comparing(RedeemableAsset::expireAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RedeemableAsset::effectiveAt)
                .thenComparing(RedeemableAsset::assetLedgerId);
        Comparator<RedeemableAsset> fifo = Comparator
                .comparing(RedeemableAsset::effectiveAt)
                .thenComparing(RedeemableAsset::assetLedgerId);
        return policy == ConsumptionPolicy.FIFO ? fifo : fefo;
    }
}
