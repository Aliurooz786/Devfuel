-- Weight history queries: no new table. Analytics reads structured_json value as kg.

CREATE INDEX idx_event_logs_weight_ts
    ON event_logs (event_timestamp DESC, logged_at DESC)
    WHERE event_type = 'WEIGHT';

COMMENT ON INDEX idx_event_logs_weight_ts IS
    'Partial index for WEIGHT series; value lives in structured_json';
