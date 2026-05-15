-- BUG-018: Enforce requires_staging = true for all trellises targeting shared plots.
-- Existing rows may have been created before the policy was enforced; this migration
-- silently fixes them so members can decrypt items via the DEK re-wrapping in staging.

UPDATE trellises t
SET requires_staging = true
FROM plots p
WHERE t.target_plot_id = p.id
  AND p.visibility = 'shared'
  AND t.requires_staging = false;
