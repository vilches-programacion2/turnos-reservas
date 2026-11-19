CREATE TABLE reservation_action (
    event_id VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    reservation_process_id VARCHAR(255) NOT NULL,
    hold_id VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    kafka_partition INTEGER NOT NULL,
    kafka_offset BIGINT NOT NULL,
    details JSONB NOT NULL,
    submission_event_id UUID UNIQUE,
    submission_state VARCHAR(24),
    CHECK ((submission_event_id IS NULL AND submission_state IS NULL)
        OR (submission_event_id IS NOT NULL AND submission_state IN ('OUTCOME_UNKNOWN', 'PUBLISHED')
            AND submission_state IS NOT NULL))
);

CREATE INDEX ix_reservation_action_process
    ON reservation_action (reservation_process_id, hold_id, event_type, kafka_offset DESC);
