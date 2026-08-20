-- Phase 1: backdated logging (additive; do not rewrite event_timestamp)

ALTER TABLE event_logs
    ADD COLUMN logged_at TIMESTAMPTZ NULL;

UPDATE event_logs
SET logged_at = created_at
WHERE logged_at IS NULL;

ALTER TABLE event_logs
    ALTER COLUMN logged_at SET NOT NULL;

ALTER TABLE event_logs
    ADD COLUMN event_time_precision VARCHAR(16) NULL;

UPDATE event_logs
SET event_time_precision = 'NOW'
WHERE event_time_precision IS NULL;

ALTER TABLE event_logs
    ALTER COLUMN event_time_precision SET NOT NULL;

ALTER TABLE event_logs
    ADD CONSTRAINT event_logs_event_time_precision_valid
        CHECK (event_time_precision IN ('NOW', 'EXACT', 'PERIOD', 'DAY', 'UNRESOLVED'));

ALTER TABLE event_logs
    ADD COLUMN event_timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata';

CREATE INDEX idx_event_logs_logged_at_desc
    ON event_logs (logged_at DESC);

COMMENT ON COLUMN event_logs.event_timestamp IS
    'When the life event occurred. Pre-V3 rows equal capture time.';
COMMENT ON COLUMN event_logs.logged_at IS
    'When the user submitted this row. Immutable after insert.';
COMMENT ON COLUMN event_logs.event_time_precision IS
    'NOW, EXACT, PERIOD, DAY, or UNRESOLVED';
COMMENT ON COLUMN event_logs.event_timezone IS
    'IANA zone used to resolve occurrence from natural language';
