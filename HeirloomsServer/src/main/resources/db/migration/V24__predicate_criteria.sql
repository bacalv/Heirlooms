-- M10 E1: Replace plot_tag_criteria junction table with JSONB criteria expression.

ALTER TABLE plots
    ADD COLUMN criteria       JSONB   NULL,
    ADD COLUMN show_in_garden BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN visibility     TEXT    NOT NULL DEFAULT 'private'
        CHECK (visibility IN ('private', 'shared', 'public'));

-- Migrate existing tag criteria to the new expression format.
-- Single-tag plots become a tag atom; multi-tag plots become an AND of tag atoms.
-- Plots with no criteria remain NULL.
UPDATE plots p
SET criteria = (
    SELECT CASE
        WHEN COUNT(*) = 0 THEN NULL
        WHEN COUNT(*) = 1 THEN jsonb_build_object('type', 'tag', 'tag', MIN(tag))
        ELSE jsonb_build_object('type', 'and', 'operands',
            jsonb_agg(jsonb_build_object('type', 'tag', 'tag', tag)
                      ORDER BY tag))
    END
    FROM plot_tag_criteria ptc
    WHERE ptc.plot_id = p.id
)
WHERE NOT p.is_system_defined;

-- System plot gets a first-class criteria expression.
UPDATE plots
    SET criteria = '{"type": "just_arrived"}'::jsonb
    WHERE is_system_defined = TRUE AND name = '__just_arrived__';

DROP TABLE plot_tag_criteria;

CREATE INDEX idx_plots_garden
    ON plots(owner_user_id, show_in_garden, sort_order)
    WHERE show_in_garden = TRUE;
