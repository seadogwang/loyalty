-- V009: outbox + inbox (design 8.9).

CREATE TABLE outbox_event (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL,
    aggregate_type varchar(64) NOT NULL,
    aggregate_id varchar(200) NOT NULL,
    event_type varchar(200) NOT NULL,
    payload jsonb NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'NEW',
    created_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz,
    CONSTRAINT ck_outbox_status CHECK (status IN ('NEW','PUBLISHED','FAILED'))
);
CREATE INDEX idx_outbox_status_time ON outbox_event(status, created_at);

CREATE TABLE inbox_event (
    consumer_name varchar(100) NOT NULL,
    event_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL,
    event_type varchar(200) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'RECEIVED',
    received_at timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz,
    error_message text,
    PRIMARY KEY (consumer_name, event_id),
    CONSTRAINT ck_inbox_status CHECK (status IN ('RECEIVED','PROCESSED','FAILED'))
);
CREATE INDEX idx_inbox_event_status
ON inbox_event(consumer_name, status, received_at);
