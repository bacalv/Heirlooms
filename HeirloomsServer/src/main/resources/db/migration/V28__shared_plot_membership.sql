-- Shared plot membership overhaul (E1): status lifecycle, local names, plot lifecycle.

-- Extend plot_members with status, member-chosen display name, and leave tracking
ALTER TABLE plot_members
    ADD COLUMN status     TEXT        NOT NULL DEFAULT 'joined'
        CHECK (status IN ('invited', 'joined', 'left')),
    ADD COLUMN local_name TEXT        NULL,
    ADD COLUMN left_at    TIMESTAMPTZ NULL;

-- Backfill: all current M10 members are already joined
UPDATE plot_members SET status = 'joined';

-- Extend plots with lifecycle columns
ALTER TABLE plots
    ADD COLUMN plot_status   TEXT        NOT NULL DEFAULT 'open'
        CHECK (plot_status IN ('open', 'closed')),
    ADD COLUMN tombstoned_at TIMESTAMPTZ NULL,
    ADD COLUMN tombstoned_by UUID        NULL REFERENCES users(id),
    ADD COLUMN created_by    UUID        NULL REFERENCES users(id);

-- Backfill: original creator is the current owner for all existing plots
UPDATE plots SET created_by = owner_user_id WHERE created_by IS NULL;

-- Fast lookups for the Shared Plots screen
CREATE INDEX idx_plot_members_user_status ON plot_members(user_id, status);

-- Tombstone cleanup job index
CREATE INDEX idx_plots_tombstoned ON plots(tombstoned_at)
    WHERE tombstoned_at IS NOT NULL;
