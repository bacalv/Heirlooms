-- V10__plots.sql
-- Garden plots: named collections whose membership is defined by tag criteria.
-- owner_user_id is nullable for v1 (single-user); FK + NOT NULL constraint added at M8.

CREATE TABLE plots (
    id                UUID        PRIMARY KEY,
    owner_user_id     UUID        NULL,
    name              TEXT        NOT NULL,
    sort_order        INT         NOT NULL DEFAULT 0,
    is_system_defined BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE plot_tag_criteria (
    plot_id UUID NOT NULL REFERENCES plots(id) ON DELETE CASCADE,
    tag     TEXT NOT NULL,
    PRIMARY KEY (plot_id, tag)
);

CREATE INDEX idx_plots_owner_sort ON plots(owner_user_id, sort_order);

-- System-defined "Just arrived" plot.
-- sort_order -1000 ensures it always sorts first.
-- name is a sentinel; application code translates it to the user-facing label at render time (D3).
INSERT INTO plots (id, owner_user_id, name, sort_order, is_system_defined)
VALUES (
    gen_random_uuid(),
    NULL,
    '__just_arrived__',
    -1000,
    TRUE
);
