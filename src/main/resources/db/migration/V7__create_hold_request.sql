CREATE TABLE hold_request
(
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    buyer_id           BIGINT        NOT NULL,
    idempotency_key    CHAR(36)      NOT NULL,
    showtime_id        BIGINT        NOT NULL,
    quantity           INT           NULL,
    seat_ids           VARCHAR(1000) NULL,
    buyer_name         VARCHAR(20)   NOT NULL,
    order_id           BIGINT        NULL,
    rejection_code     VARCHAR(40)   NULL,
    rejection_detail   VARCHAR(255)  NULL,
    rejection_seat_ids VARCHAR(1000) NULL,
    recorded_at        DATETIME(6)   NOT NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_hold_request_buyer_key UNIQUE (buyer_id, idempotency_key),
    CONSTRAINT fk_hold_request_showtime FOREIGN KEY (showtime_id) REFERENCES showtime (id),
    CONSTRAINT fk_hold_request_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_hold_request_single_result CHECK (order_id IS NULL OR rejection_code IS NULL),
    CONSTRAINT ck_hold_request_seat_choice CHECK ((quantity IS NULL) <> (seat_ids IS NULL))
);
