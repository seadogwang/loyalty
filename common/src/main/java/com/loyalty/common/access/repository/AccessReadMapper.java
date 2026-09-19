package com.loyalty.common.access.repository;

import com.loyalty.common.access.domain.EffectiveGrant;
import com.loyalty.common.access.domain.RoleBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * Read-side authorization DAO (MyBatis, design 27.2). Used by the enforcement layer in
 * every service (shared via {@code common}). Effective grants are cached by
 * {@link com.loyalty.common.access.authorization.EffectiveGrantCache}.
 */
@Mapper
public interface AccessReadMapper {

    UUID findPrincipalId(@Param("principalType") String principalType, @Param("subject") String subject);

    List<EffectiveGrant> findEffectiveGrants(@Param("principalId") UUID principalId);

    List<RoleBinding> findActiveBindings(@Param("principalId") UUID principalId);
}
