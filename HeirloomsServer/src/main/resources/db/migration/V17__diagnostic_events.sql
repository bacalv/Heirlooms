CREATE TABLE diagnostic_events (
    id           UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    device_label TEXT      NOT NULL DEFAULT '',
    tag          TEXT      NOT NULL,
    message      TEXT      NOT NULL,
    detail       TEXT      NOT NULL DEFAULT ''
);
