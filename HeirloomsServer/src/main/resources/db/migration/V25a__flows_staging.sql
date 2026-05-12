-- M10 E2: Collection plots (plot_items), flows, and staging decisions.

-- Explicit item collections for non-criteria-driven plots
CREATE TABLE plot_items (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    plot_id               UUID        NOT NULL REFERENCES plots(id) ON DELETE CASCADE,
    upload_id             UUID        NOT NULL REFERENCES uploads(id),
    added_by              UUID        NOT NULL REFERENCES users(id),
    source_flow_id        UUID        NULL,
    -- E2EE columns: NULL for private plots; populated in E3 for shared plots
    wrapped_item_dek      BYTEA       NULL,
    item_dek_format       TEXT        NULL,
    wrapped_thumbnail_dek BYTEA       NULL,
    thumbnail_dek_format  TEXT        NULL,
    added_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (plot_id, upload_id)
);

-- Flows: criteria expression → target collection plot
CREATE TABLE flows (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID        NOT NULL REFERENCES users(id),
    name             TEXT        NOT NULL,
    criteria         JSONB       NOT NULL,
    target_plot_id   UUID        NOT NULL REFERENCES plots(id),
    requires_staging BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE plot_items
    ADD CONSTRAINT fk_plot_items_flow
    FOREIGN KEY (source_flow_id) REFERENCES flows(id) ON DELETE SET NULL;

-- Per-item staging decisions scoped to the plot (not the flow)
-- No row = pending (appears in staging if criteria still matches)
CREATE TABLE plot_staging_decisions (
    plot_id        UUID        NOT NULL REFERENCES plots(id)   ON DELETE CASCADE,
    upload_id      UUID        NOT NULL REFERENCES uploads(id),
    decision       TEXT        NOT NULL CHECK (decision IN ('approved', 'rejected')),
    source_flow_id UUID        NULL     REFERENCES flows(id)   ON DELETE SET NULL,
    decided_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (plot_id, upload_id)
);

CREATE INDEX idx_plot_staging ON plot_staging_decisions(plot_id, decision);
CREATE INDEX idx_flows_user   ON flows(user_id);
