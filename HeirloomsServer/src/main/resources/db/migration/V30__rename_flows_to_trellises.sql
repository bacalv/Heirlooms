-- REF-001: Rename "flows" to "trellises" throughout the schema.
-- Renames the flows table, and the source_flow_id columns in plot_items
-- and plot_staging_decisions.

ALTER TABLE flows RENAME TO trellises;

-- Re-point the index on the renamed table
ALTER INDEX idx_flows_user RENAME TO idx_trellises_user;

-- Rename source_flow_id → source_trellis_id in plot_items
ALTER TABLE plot_items RENAME COLUMN source_flow_id TO source_trellis_id;

-- The FK constraint fk_plot_items_flow still references trellises(id)
-- (PostgreSQL automatically updates the FK target when the table is renamed).
-- We rename the constraint for clarity.
ALTER TABLE plot_items RENAME CONSTRAINT fk_plot_items_flow TO fk_plot_items_trellis;

-- Rename source_flow_id → source_trellis_id in plot_staging_decisions
ALTER TABLE plot_staging_decisions RENAME COLUMN source_flow_id TO source_trellis_id;

-- Rename the target_plot_id FK constraint on trellises for clarity
ALTER TABLE trellises RENAME CONSTRAINT flows_target_plot_id_fkey TO trellises_target_plot_id_fkey;
