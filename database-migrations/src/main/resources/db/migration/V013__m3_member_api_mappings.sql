-- V013: M3 API-permission mappings for member create/get (idempotent).
-- Design 27.3: every API must map to a permission; unmapped APIs are denied by default.

INSERT INTO auth_permission (code, resource, action, description, managed) VALUES
    ('member.create', 'member', 'create', 'Create member + bind initial identity', true)
ON CONFLICT (code) DO NOTHING;

-- PROGRAM_ADMIN, MEMBER_SUPPORT, INTEGRATION_CLIENT may create members.
INSERT INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM auth_role r, auth_permission p
WHERE p.code = 'member.create'
  AND r.code IN ('PROGRAM_ADMIN','MEMBER_SUPPORT','INTEGRATION_CLIENT')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO auth_api_permission (http_method, path_template, permission_id, scope_type)
SELECT v.method, v.path, p.id, 'PROGRAM'
FROM (VALUES
    ('POST', '/api/v1/programs/{programId}/members',                       'member.create'),
    ('GET',  '/api/v1/programs/{programId}/members/{memberId}',            'member.read')
) AS v(method, path, code)
JOIN auth_permission p ON p.code = v.code
ON CONFLICT (http_method, path_template) DO NOTHING;
