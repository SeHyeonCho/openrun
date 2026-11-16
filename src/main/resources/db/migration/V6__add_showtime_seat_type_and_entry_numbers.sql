ALTER TABLE showtime
    ADD COLUMN seat_type VARCHAR(20) NOT NULL DEFAULT 'RESERVED' AFTER ends_at;
UPDATE showtime s
SET seat_type = 'GENERAL'
WHERE EXISTS (SELECT 1 FROM seat WHERE seat.showtime_id = s.id AND seat.type = 'GENERAL');
ALTER TABLE showtime
    ALTER COLUMN seat_type DROP DEFAULT,
    ADD CONSTRAINT ck_showtime_seat_type CHECK (seat_type IN ('RESERVED', 'GENERAL'));

CREATE INDEX idx_seat_showtime_status ON seat (showtime_id, status);

CREATE TABLE showtime_entry_sequence
(
    showtime_id BIGINT NOT NULL,
    last_number INT    NOT NULL,
    PRIMARY KEY (showtime_id),
    CONSTRAINT fk_showtime_entry_sequence_showtime FOREIGN KEY (showtime_id) REFERENCES showtime (id),
    CONSTRAINT ck_showtime_entry_sequence_last_number CHECK (last_number >= 0)
);

CREATE TABLE order_entry_number
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    order_id     BIGINT      NOT NULL,
    showtime_id  BIGINT      NOT NULL,
    entry_number INT         NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_order_entry_number_showtime_number UNIQUE (showtime_id, entry_number),
    INDEX idx_order_entry_number_order (order_id),
    CONSTRAINT fk_order_entry_number_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_entry_number_showtime FOREIGN KEY (showtime_id) REFERENCES showtime (id),
    CONSTRAINT ck_order_entry_number_positive CHECK (entry_number > 0)
);
