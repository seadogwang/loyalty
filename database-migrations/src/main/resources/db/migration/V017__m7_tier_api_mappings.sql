-- V017: M7 tier evaluation + benefit API mappings (idempotent).
INSERT INTO auth_api_permission (http_method, path_template, permission_id, scope_type)
SELECT v.method, v.path, p.id, 'PROGRAM'
FROM (VALUES
    ('POST', '/api/v1/programs/{programId}/members/{memberId}/tier-evaluations',            'tier.evaluation.execute'),
    ('POST', '/api/v1/programs/{programId}/members/{memberId}/tier-evaluations/recalculate','tier.evaluation.execute'),
    ('GET',  '/api/v1/programs/{programId}/members/{memberId}/tiers',                        'tier.evaluation.execute'),
    ('GET',  '/api/v1/programs/{programId}/members/{memberId}/tiers/{schemeId}/history',      'tier.evaluation.execute'),
    ('GET',  '/api/v1/programs/{programId}/tier-schemes/{schemeId}',                          'program.config.read')
) AS v(method, path, code)
JOIN auth_permission p ON p.code = v.code
ON CONFLICT (http_method, path_template) DO NOTHING;
