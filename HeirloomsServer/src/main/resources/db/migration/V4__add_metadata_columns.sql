ALTER TABLE uploads
    ADD COLUMN captured_at  TIMESTAMPTZ,
    ADD COLUMN latitude     DOUBLE PRECISION,
    ADD COLUMN longitude    DOUBLE PRECISION,
    ADD COLUMN altitude     DOUBLE PRECISION,
    ADD COLUMN device_make  VARCHAR(128),
    ADD COLUMN device_model VARCHAR(128);
