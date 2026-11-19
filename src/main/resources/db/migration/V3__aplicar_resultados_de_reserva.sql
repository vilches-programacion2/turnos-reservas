ALTER TABLE reservation_action
    ADD COLUMN processing_status VARCHAR(16) CHECK (processing_status IN ('APPLIED', 'IGNORED')),
    ADD COLUMN submission_started_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX ix_reservation_action_pending
    ON reservation_action (reservation_process_id, hold_id, kafka_partition, kafka_offset)
    WHERE processing_status IS NULL AND event_type <> 'AdditionalInformationRequested';
