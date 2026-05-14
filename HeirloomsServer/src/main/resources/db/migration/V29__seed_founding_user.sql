-- V29__seed_founding_user.sql
-- Seeds the well-known founding user row so the static API key path works on
-- fresh databases (test environments, local dev). The founding user has no
-- auth credentials set — login via the normal password flow is impossible.
-- All actions taken via the API key are attributed to this user.
INSERT INTO users (id, username, display_name, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    '_founding',
    'Founding User',
    '2024-01-01 00:00:00+00'
) ON CONFLICT (id) DO NOTHING;

-- Seed the founding user's system plot (required by the app on first login).
INSERT INTO plots (id, owner_user_id, name, sort_order, is_system_defined, show_in_garden, visibility, plot_status, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    'My Garden',
    0,
    true,
    true,
    'private',
    'open',
    '2024-01-01 00:00:00+00',
    '2024-01-01 00:00:00+00'
) ON CONFLICT (id) DO NOTHING;
