-- V018: M8 order/return/exchange/recalculate API mappings (idempotent).
INSERT INTO auth_api_permission (http_method, path_template, permission_id, scope_type)
SELECT v.method, v.path, p.id, 'PROGRAM'
FROM (VALUES
    ('POST', '/api/v1/programs/{programId}/orders/complete',          'point.operation.earn'),
    ('POST', '/api/v1/programs/{programId}/returns/complete',          'point.operation.reverse'),
    ('POST', '/api/v1/programs/{programId}/exchanges/complete',        'point.operation.earn'),
    ('POST', '/api/v1/programs/{programId}/members/{memberId}/point-operations/recalculate', 'point.operation.recalculate')
) AS v(method, path, code)
JOIN auth_permission p ON p.code = v.code
ON CONFLICT (http_method, path_template) DO NOTHING;
