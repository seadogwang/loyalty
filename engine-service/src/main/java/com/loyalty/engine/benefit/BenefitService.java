package com.loyalty.engine.benefit;

import com.loyalty.engine.benefit.domain.MemberBenefit;
import com.loyalty.engine.benefit.infrastructure.MemberBenefitMapper;
import com.loyalty.engine.benefit.infrastructure.TierBenefitMappingViewMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Benefit grant runtime (design 12 / M7-T03). Grants the benefits mapped to a tier to a
 * member; idempotent — replaying a tier-changed event does not create duplicate ACTIVE
 * member_benefit rows.
 */
@Service
public class BenefitService {

    private static final Logger log = LoggerFactory.getLogger(BenefitService.class);

    private final MemberBenefitMapper memberBenefitMapper;
    private final TierBenefitMappingViewMapper tierBenefitMappingViewMapper;

    public BenefitService(MemberBenefitMapper memberBenefitMapper,
                         TierBenefitMappingViewMapper tierBenefitMappingViewMapper) {
        this.memberBenefitMapper = memberBenefitMapper;
        this.tierBenefitMappingViewMapper = tierBenefitMappingViewMapper;
    }

    @Transactional
    public int grantForTier(UUID tenantId, UUID programId, UUID memberId, UUID tierId,
                          String sourceType, String sourceId) {
        List<UUID> benefitIds = tierBenefitMappingViewMapper.findBenefitIdsByTier(tenantId, programId, tierId);
        int granted = 0;
        for (UUID benefitId : benefitIds) {
            MemberBenefit existing = memberBenefitMapper.findActiveByMemberAndBenefit(tenantId, programId, memberId, benefitId);
            if (existing != null) continue; // idempotent: already granted
            memberBenefitMapper.insert(UUID.randomUUID(), tenantId, programId, memberId, benefitId, sourceType, sourceId);
            granted++;
            log.debug("granted benefit {} to member {} (tier {})", benefitId, memberId, tierId);
        }
        return granted;
    }

    public List<MemberBenefit> listActive(UUID tenantId, UUID programId, UUID memberId) {
        return memberBenefitMapper.findActiveByMember(tenantId, programId, memberId);
    }
}
