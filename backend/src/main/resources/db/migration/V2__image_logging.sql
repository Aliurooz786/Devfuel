-- Phase 5A: image logging (additive)

ALTER TABLE event_logs
    ADD COLUMN image_ref VARCHAR(512);

ALTER TABLE event_logs
    ADD COLUMN raw_model_output JSONB;

COMMENT ON COLUMN event_logs.image_ref IS 'Relative path to uploaded image; null for text logs';
COMMENT ON COLUMN event_logs.raw_model_output IS 'Full Vision/parser JSON payload; null for older rows';
