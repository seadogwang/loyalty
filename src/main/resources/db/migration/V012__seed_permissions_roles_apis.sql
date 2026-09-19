-- V012: seed built-in permissions, roles, role-permission matrix, and API permission mapping.
-- Design 27.1 / 27.3. Idempotent: stable business keys + ON CONFLICT DO NOTHING.

-- ---- Permissions -----------------------------------------------------------
INSERT INTO auth_permission (code, resource, action, description, managed) VALUES
    ('program.config.read',       'program.config',  'read',      'Read Program configuration', true),
    ('program.config.write',      'program.config',  'write',     'Modify Program configuration', true),
    ('member.read',               'member',           'read',      'Query members', true),
    ('member.identity.write',     'member.identity',  'write',     'Bind/replace/revoke identity', true),
    ('member.merge.execute',       'member.merge',     'execute',  'Execute member merge', true),
    ('point.balance.read',         'point.balance',    'read',      'Query point balances', true),
    ('point.ledger.read',          'point.ledger',     'read',      'Query point ledger', true),
    ('point.operation.earn',       'point.operation',  'earn',      'Earn points', true),
    ('point.operation.redeem',      'point.operation',  'redeem',    'Redeem points', true),
    ('point.operation.reverse',    'point.operation',  'reverse',   'Reverse a prior movement', true),
    ('point.operation.restore',    'point.operation',  'restore',   'Restore consumed points', true),
    ('point.operation.expire',     'point.operation',  'expire',    'Expire points', true),
    ('point.operation.adjust',     'point.operation',  'adjust',    'Adjust points', true),
    ('point.operation.recalculate', 'point.operation', 'recalculate', 'Recalculate points', true),
    ('tier.evaluation.execute',    'tier.evaluation',  'execute',  'Execute tier evaluation', true),
    ('rule.version.write',         'rule.version',      'write',     'Create/test rule version', true),
    ('rule.version.publish',       'rule.version',      'publish',   'Publish rule version', true),
    ('role.binding.write',         'role.binding',      'write',     'Grant/revoke role bindings', true),
    ('audit.read',                 'audit',             'read',      'Read authorization/business audit', true)
ON CONFLICT (code) DO NOTHING;

-- ---- Built-in roles (system-managed templates, SYSTEM scope) ----------------
INSERT INTO auth_role (code, name, scope_key, managed, status) VALUES
    ('PLATFORM_ADMIN',    'Platform Administrator', 'SYSTEM', true, 'ACTIVE'),
    ('TENANT_ADMIN',      'Tenant Administrator',   'SYSTEM', true, 'ACTIVE'),
    ('PROGRAM_ADMIN',     'Program Administrator',  'SYSTEM', true, 'ACTIVE'),
    ('POINT_OPERATOR',    'Point Operator',         'SYSTEM', true, 'ACTIVE'),
    ('POINT_AUDITOR',     'Point Auditor',           'SYSTEM', true, 'ACTIVE'),
    ('TIER_ADMIN',        'Tier Administrator',      'SYSTEM', true, 'ACTIVE'),
    ('RULE_AUTHOR',       'Rule Author',             'SYSTEM', true, 'ACTIVE'),
    ('RULE_PUBLISHER',    'Rule Publisher',           'SYSTEM', true, 'ACTIVE'),
    ('MEMBER_SUPPORT',    'Member Support',           'SYSTEM', true, 'ACTIVE'),
    ('INTEGRATION_CLIENT','Integration Client',      'SYSTEM', true, 'ACTIVE'),
    ('READ_ONLY',         'Read Only',                'SYSTEM', true, 'ACTIVE')
ON CONFLICT (scope_key, code) DO NOTHING;

-- Helper view of role id by code for seeding role_permission.
-- ---- Role <-> Permission matrix -------------------------------------------
-- PLATFORM_ADMIN: all managed permissions.
INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth_role r, auth_permission p
WHERE r.scope_key = 'SYSTEM' AND r.code = 'PLATFORM_ADMIN' AND p.managed = true
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- TENANT_ADMIN
INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM auth_role r, auth_permission p
WHERE r.code = 'TENANT_ADMIN' AND p.code IN (
    'program.config.read','program.config.write','member.read','member.identity.write',
    'member.merge.execute','point.balance.read','point.ledger.read','tier.evaluation.execute',
    'rule.version.write','role.binding.write','audit.read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- PROGRAM_ADMIN
INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM auth_role r, auth_permission p
WHERE r.code = 'PROGRAM_ADMIN' AND p.code IN (
    'program.config.read','program.config.write','member.read','member.identity.write',
    'member.merge.execute','point.balance.read','point.ledger.read',
    'point.operation.earn','point.operation.redeem','point.operation.reverse','point.operation.restore',
    'point.operation.expire','point.operation.adjust','point.operation.recalculate',
    'tier.evaluation.execute','audit.read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- POINT_OPERATOR
INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM auth_role r, auth_permission p
WHERE r.code = 'POINT_OPERATOR' AND p.code IN (
    'point.operation.earn','point.operation.redeem','point.operation.reverse',
    'point.operation.restore','point.operation.expire','point.balance.read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- POINT_AUDITOR
INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM auth_role r, auth_permission p
WHERE r.code = 'POINT_AUDITOR' AND p.code IN ('point.balance.read','point.ledger.read','audit.read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- TIER_ADMIN
INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM auth_role r, auth_permission p
WHERE r.code = 'TIER_ADMIN' AND p.code IN ('tier.evaluation.execute','point.balance.read','point.ledger.read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- RULE_AUTHOR
INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM auth_role r, auth_permission p
WHERE r.code = 'RULE_AUTHOR' AND p.code IN ('rule.version.write')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- RULE_PUBLISHER
INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM auth_role r, auth_permission p
WHERE r.code = 'RULE_PUBLISHER' AND p.code IN ('rule.version.write','rule.version.publish')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- MEMBER_SUPPORT
INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM auth_role r, auth_permission p
WHERE r.code = 'MEMBER_SUPPORT' AND p.code IN (
    'member.read','member.identity.write','member.merge.execute','point.balance.read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- INTEGRATION_CLIENT
INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM auth_role r, auth_permission p
WHERE r.code = 'INTEGRATION_CLIENT' AND p.code IN (
    'point.operation.earn','point.operation.redeem','point.balance.read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- READ_ONLY
INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM auth_role r, auth_permission p
WHERE r.code = 'READ_ONLY' AND p.code IN (
    'program.config.read','member.read','point.balance.read','point.ledger.read','audit.read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ---- API permission mapping (design 24.x / 27.3 / 27.4) --------------------
-- Business APIs are PROGRAM-scoped; admin APIs are SYSTEM-scoped.
INSERT INTO auth_api_permission (http_method, path_template, permission_id, scope_type)
SELECT v.method, v.path, p.id, v.scope
FROM (VALUES
    ('GET',  '/api/v1/programs/{programId}/members/{memberId}/accounts/{accountId}/balances',          'point.balance.read',         'PROGRAM'),
    ('GET',  '/api/v1/programs/{programId}/members/{memberId}/accounts/{accountId}/ledger',            'point.ledger.read',          'PROGRAM'),
    ('POST', '/api/v1/programs/{programId}/members/{memberId}/accounts/{accountId}/point-operations/earn',       'point.operation.earn',        'PROGRAM'),
    ('POST', '/api/v1/programs/{programId}/members/{memberId}/accounts/{accountId}/point-operations/redeem',     'point.operation.redeem',     'PROGRAM'),
    ('POST', '/api/v1/programs/{programId}/members/{memberId}/accounts/{accountId}/point-operations/reverse',    'point.operation.reverse',    'PROGRAM'),
    ('POST', '/api/v1/programs/{programId}/members/{memberId}/accounts/{accountId}/point-operations/restore',    'point.operation.restore',    'PROGRAM'),
    ('POST', '/api/v1/programs/{programId}/members/{memberId}/accounts/{accountId}/point-operations/adjust',     'point.operation.adjust',     'PROGRAM'),
    ('POST', '/api/v1/programs/{programId}/members/{memberId}/accounts/{accountId}/point-operations/recalculate', 'point.operation.recalculate','PROGRAM'),
    ('POST', '/api/v1/programs/{programId}/members/resolve',                                            'member.read',                'PROGRAM'),
    ('POST', '/api/v1/programs/{programId}/members/{memberId}/identities',                               'member.identity.write',      'PROGRAM'),
    ('POST', '/api/v1/programs/{programId}/members/{memberId}/identities/{identityId}/replace',        'member.identity.write',      'PROGRAM'),
    ('POST', '/api/v1/programs/{programId}/members/{memberId}/identities/{identityId}/revoke',          'member.identity.write',      'PROGRAM'),
    ('GET',  '/api/v1/programs/{programId}/members/{memberId}/identities',                              'member.read',                'PROGRAM'),
    ('POST', '/api/v1/programs/{programId}/members/{sourceMemberId}/merge',                            'member.merge.execute',       'PROGRAM'),
    ('POST', '/api/v1/programs/{programId}/members/{memberId}/tier-evaluations',                        'tier.evaluation.execute',   'PROGRAM'),
    ('POST', '/api/v1/programs/{programId}/members/{memberId}/tier-evaluations/recalculate',            'tier.evaluation.execute',   'PROGRAM'),
    ('GET',  '/api/v1/programs/{programId}/members/{memberId}/tiers',                                   'tier.evaluation.execute',   'PROGRAM'),
    ('GET',  '/api/v1/programs/{programId}/members/{memberId}/tiers/{schemeId}/history',                'tier.evaluation.execute',   'PROGRAM'),
    ('GET',  '/api/v1/programs/{programId}/tier-schemes/{schemeId}',                                    'program.config.read',        'PROGRAM'),
    ('GET',  '/api/v1/admin/permissions',                                                                'role.binding.write',        'SYSTEM'),
    ('GET',  '/api/v1/admin/roles',                                                                      'role.binding.write',        'SYSTEM'),
    ('POST', '/api/v1/admin/roles',                                                                      'role.binding.write',        'SYSTEM'),
    ('GET',  '/api/v1/admin/roles/{roleId}',                                                            'role.binding.write',        'SYSTEM'),
    ('PATCH', '/api/v1/admin/roles/{roleId}',                                                           'role.binding.write',        'SYSTEM'),
    ('POST', '/api/v1/admin/roles/{roleId}/deactivate',                                                  'role.binding.write',        'SYSTEM'),
    ('POST', '/api/v1/admin/principals/{principalId}/role-bindings',                                    'role.binding.write',        'SYSTEM'),
    ('DELETE', '/api/v1/admin/principals/{principalId}/role-bindings/{bindingId}',                      'role.binding.write',        'SYSTEM'),
    ('GET',  '/api/v1/admin/principals/{principalId}/effective-permissions',                            'audit.read',                 'SYSTEM'),
    ('GET',  '/api/v1/admin/authorization-audit',                                                        'audit.read',                 'SYSTEM'),
    ('POST', '/api/v1/admin/authorization-approvals',                                                    'role.binding.write',        'SYSTEM'),
    ('POST', '/api/v1/admin/authorization-approvals/{approvalId}/approve',                              'role.binding.write',        'SYSTEM'),
    ('POST', '/api/v1/admin/authorization-approvals/{approvalId}/reject',                               'role.binding.write',        'SYSTEM')
) AS v(method, path, code, scope)
JOIN auth_permission p ON p.code = v.code
ON CONFLICT (http_method, path_template) DO NOTHING;
