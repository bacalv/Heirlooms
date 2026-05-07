-- V7__capsules.sql
-- Milestone 5 Increment 1 — capsule data model.

CREATE TABLE capsules (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_user TEXT NOT NULL,
    shape TEXT NOT NULL,
        -- 'open' or 'sealed'. Determines whether contents can be edited.
    state TEXT NOT NULL,
        -- 'open', 'sealed', 'delivered', 'cancelled'.
    unlock_at TIMESTAMP WITH TIME ZONE NOT NULL,
        -- The instant at which this capsule becomes available to recipients.
        -- 8am sender's local time on the chosen date, by application convention.
    cancelled_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT capsules_shape_valid
        CHECK (shape IN ('open', 'sealed')),
    CONSTRAINT capsules_state_valid
        CHECK (state IN ('open', 'sealed', 'delivered', 'cancelled')),
    CONSTRAINT capsules_state_consistent_with_shape
        CHECK (
            (shape = 'open'   AND state IN ('open', 'sealed', 'delivered', 'cancelled')) OR
            (shape = 'sealed' AND state IN ('sealed', 'delivered', 'cancelled'))
        )
);

CREATE INDEX capsules_state_idx ON capsules (state);

-- Partial index — only capsules that might still deliver.
CREATE INDEX capsules_unlock_at_idx ON capsules (unlock_at)
    WHERE state IN ('open', 'sealed');

-- Many-to-many: photos in a capsule. One photo can be in many capsules.
CREATE TABLE capsule_contents (
    capsule_id UUID NOT NULL REFERENCES capsules(id) ON DELETE CASCADE,
    upload_id  UUID NOT NULL REFERENCES uploads(id)  ON DELETE CASCADE,
    added_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (capsule_id, upload_id)
);

-- For "which capsules contain this photo?" — the photo detail view's lookup.
CREATE INDEX capsule_contents_upload_idx ON capsule_contents (upload_id);

-- Recipients: free-text in v1, references to connections in Milestone 7.
CREATE TABLE capsule_recipients (
    id         UUID PRIMARY KEY,
    capsule_id UUID NOT NULL REFERENCES capsules(id) ON DELETE CASCADE,
    recipient  TEXT NOT NULL,
    added_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX capsule_recipients_capsule_idx ON capsule_recipients (capsule_id);

-- Messages: editable until delivery. Versioned so history is recoverable.
CREATE TABLE capsule_messages (
    id         UUID PRIMARY KEY,
    capsule_id UUID NOT NULL REFERENCES capsules(id) ON DELETE CASCADE,
    body       TEXT NOT NULL,
    version    INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (capsule_id, version)
);

-- For "fetch current message" — highest version first.
CREATE INDEX capsule_messages_capsule_version_idx
    ON capsule_messages (capsule_id, version DESC);
