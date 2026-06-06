-- V6 (Day 3.3): the outbox becomes a general mechanism for more than one event type.
-- Each outbox row now carries its destination topic, so the relay routes by the row itself rather
-- than hardcoding a single topic. Existing rows default to the accrual topic.

ALTER TABLE outbox_event ADD COLUMN topic VARCHAR(100) NOT NULL DEFAULT 'points.accrued';
