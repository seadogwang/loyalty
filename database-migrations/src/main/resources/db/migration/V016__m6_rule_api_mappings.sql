-- V016: M6 rule lifecycle API-permission mappings (idempotent).
INSERT INTO auth_api_permission (http_method, path_template, permission_id, scope_type)
SELECT v.method, v.path, p.id, 'PROGRAM'
FROM (VALUES
    ('POST', '/api/v1/programs/{programId}/rules',                                    'rule.version.write'),
    ('GET',  '/api/v1/programs/{programId}/rules',                                     'rule.version.write'),
    ('POST', '/api/v1/programs/{programId}/rules/{ruleDefinitionId}/versions',         'rule.version.write'),
    ('POST', '/api/v1/programs/{programId}/rules/versions/{versionId}/publish',         'rule.version.publish'),
    ('POST', '/api/v1/programs/{programId}/rules/versions/{versionId}/retire',          'rule.version.publish'),
    ('GET',  '/api/v1/programs/{programId}/rules/{ruleDefinitionId}/published',         'rule.version.write')
) AS v(method, path, code)
JOIN auth_permission p ON p.code = v.code
ON CONFLICT (http_method, path_template) DO NOTHING;
