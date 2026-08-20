-- DevFuel Phase 0 — Database Schema
-- Design freeze. Apply via Flyway/Liquibase or manual psql.
-- Source of truth for columns: 04_Data_Model.md + 09_Technical_Design.md

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------------------------
-- event_logs: one row per natural-language life event
-- ---------------------------------------------------------------------------
CREATE TABLE event_logs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_timestamp  TIMESTAMPTZ NOT NULL,
    raw_text         TEXT NOT NULL,
    event_type       VARCHAR(32) NOT NULL,
    structured_json  JSONB NOT NULL DEFAULT '{}'::jsonb,
    source           VARCHAR(32) NOT NULL DEFAULT 'web',
    parser_version   VARCHAR(64) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT event_logs_raw_text_not_blank
        CHECK (length(trim(raw_text)) > 0),

    CONSTRAINT event_logs_source_not_blank
        CHECK (length(trim(source)) > 0),

    CONSTRAINT event_logs_parser_version_not_blank
        CHECK (length(trim(parser_version)) > 0),

    CONSTRAINT event_logs_event_type_valid
        CHECK (event_type IN (
            'SMOKING',
            'FOOD',
            'WEIGHT',
            'EXERCISE',
            'ALCOHOL',
            'MOOD',
            'SLEEP',
            'NOTE',
            'UNKNOWN'
        ))
);

COMMENT ON TABLE event_logs IS 'Phase 0 truth collection: raw + typed structured life events';
COMMENT ON COLUMN event_logs.event_timestamp IS 'When the life event is recorded (Phase 0: server time at create)';
COMMENT ON COLUMN event_logs.raw_text IS 'Exact user message';
COMMENT ON COLUMN event_logs.event_type IS 'Classifier output; UNKNOWN if unclear or parse failed';
COMMENT ON COLUMN event_logs.structured_json IS 'Type-specific fields from OpenAI; may be empty object';
COMMENT ON COLUMN event_logs.source IS 'Client origin of the log; default web';
COMMENT ON COLUMN event_logs.parser_version IS 'Parser/prompt version that produced structured_json';

-- Timeline: newest first
CREATE INDEX idx_event_logs_event_timestamp_desc
    ON event_logs (event_timestamp DESC);

-- Optional filter aid for search by event_type
CREATE INDEX idx_event_logs_event_type
    ON event_logs (event_type);

-- Phase 0 search uses ILIKE '%q%' on raw_text.
-- No dedicated search index: volume stays small during 14-day logging.
-- (Do not add pg_trgm / vector indexes in Phase 0.)

-- Optional helper for updated_at maintenance (app may also set explicitly)
CREATE OR REPLACE FUNCTION set_event_logs_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_event_logs_updated_at ON event_logs;

CREATE TRIGGER trg_event_logs_updated_at
    BEFORE UPDATE ON event_logs
    FOR EACH ROW
    EXECUTE PROCEDURE set_event_logs_updated_at();
