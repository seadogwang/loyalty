-- V010: authorization / permission / approval / audit (design 8.10).

CREATE TABLE auth_principal (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    principal_type varchar(32) NOT NULL,
    subject varchar(256) NOT NULL,
    display_name varchar(200),
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_auth_principal_subject UNIQUE (principal_type, subject),
    CONSTRAINT ck_auth_principal_type
        CHECK (principal_type IN ('USER','SERVICE_ACCOUNT','API_CLIENT')),
    CONSTRAINT ck_auth_principal_status
        CHECK (status IN ('ACTIVE','SUSPENDED','REVOKED'))
);

CREATE TABLE auth_permission (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(150) NOT NULL,
    resource varchar(100) NOT NULL,
    action varchar(32) NOT NULL,
    description varchar(500),
    managed boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_auth_permission_code UNIQUE (code)
);

CREATE TABLE auth_role (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(100) NOT NULL,
    name varchar(200) NOT NULL,
    scope_key varchar(200) NOT NULL DEFAULT 'SYSTEM',
    tenant_id uuid REFERENCES tenant(id),
    program_id uuid REFERENCES program(id),
    managed boolean NOT NULL DEFAULT false,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_auth_role_scope_code UNIQUE (scope_key, code),
    CONSTRAINT ck_auth_role_scope
        CHECK ((program_id IS NULL) OR (tenant_id IS NOT NULL)),
    CONSTRAINT ck_auth_role_status
        CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE auth_role_permission (
    role_id uuid NOT NULL REFERENCES auth_role(id),
    permission_id uuid NOT NULL REFERENCES auth_permission(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE auth_principal_role (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    principal_id uuid NOT NULL REFERENCES auth_principal(id),
    role_id uuid NOT NULL REFERENCES auth_role(id),
    scope_type varchar(16) NOT NULL,
    tenant_id uuid REFERENCES tenant(id),
    program_id uuid REFERENCES program(id),
    effective_from timestamptz NOT NULL DEFAULT now(),
    effective_to timestamptz,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    created_by varchar(200),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_auth_assignment_scope
        CHECK (
            (scope_type = 'SYSTEM' AND tenant_id IS NULL AND program_id IS NULL)
            OR (scope_type = 'TENANT' AND tenant_id IS NOT NULL AND program_id IS NULL)
            OR (scope_type = 'PROGRAM' AND tenant_id IS NOT NULL AND program_id IS NOT NULL)
        ),
    CONSTRAINT ck_auth_assignment_status
        CHECK (status IN ('ACTIVE','REVOKED'))
);
CREATE UNIQUE INDEX uk_auth_principal_role_active
ON auth_principal_role(principal_id, role_id, scope_type,
    COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid),
    COALESCE(program_id, '00000000-0000-0000-0000-000000000000'::uuid))
WHERE status = 'ACTIVE' AND effective_to IS NULL;

CREATE TABLE auth_api_permission (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    http_method varchar(16) NOT NULL,
    path_template varchar(500) NOT NULL,
    permission_id uuid NOT NULL REFERENCES auth_permission(id),
    scope_type varchar(16) NOT NULL DEFAULT 'PROGRAM',
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_auth_api_permission UNIQUE (http_method, path_template),
    CONSTRAINT ck_auth_api_scope
        CHECK (scope_type IN ('SYSTEM','TENANT','PROGRAM')),
    CONSTRAINT ck_auth_api_status
        CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE authorization_audit (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_principal_id uuid REFERENCES auth_principal(id),
    action varchar(32) NOT NULL,
    target_type varchar(64) NOT NULL,
    target_id varchar(200),
    permission_code varchar(150),
    tenant_id uuid REFERENCES tenant(id),
    program_id uuid REFERENCES program(id),
    request_id varchar(200),
    correlation_id varchar(200),
    detail_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_authorization_audit_action
        CHECK (action IN ('AUTHZ_ALLOWED','AUTHZ_DENIED','ROLE_GRANTED','ROLE_REVOKED','ROLE_CREATED','ROLE_UPDATED','APPROVAL_REQUESTED','APPROVAL_GRANTED','APPROVAL_REJECTED'))
);

CREATE TABLE authorization_approval (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_type varchar(64) NOT NULL,
    request_id varchar(200) NOT NULL,
    requester_principal_id uuid NOT NULL REFERENCES auth_principal(id),
    approver_principal_id uuid REFERENCES auth_principal(id),
    required_permission varchar(150) NOT NULL,
    tenant_id uuid REFERENCES tenant(id),
    program_id uuid REFERENCES program(id),
    status varchar(32) NOT NULL DEFAULT 'REQUESTED',
    reason varchar(500) NOT NULL,
    requested_at timestamptz NOT NULL DEFAULT now(),
    decided_at timestamptz,
    CONSTRAINT uk_authorization_approval_request UNIQUE (request_type, request_id),
    CONSTRAINT ck_authorization_approval_status
        CHECK (status IN ('REQUESTED','APPROVED','REJECTED','EXPIRED')),
    CONSTRAINT ck_authorization_approval_actor
        CHECK (approver_principal_id IS NULL OR approver_principal_id <> requester_principal_id),
    CONSTRAINT ck_authorization_approval_scope
        CHECK (program_id IS NULL OR tenant_id IS NOT NULL)
);
CREATE INDEX idx_auth_principal_role_scope
ON auth_principal_role(principal_id, scope_type, tenant_id, program_id, status);
CREATE INDEX idx_authorization_audit_scope_time
ON authorization_audit(tenant_id, program_id, created_at);
