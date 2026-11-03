CREATE TABLE orders
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    showtime_id BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    held_until  DATETIME(6) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_orders_showtime_user (showtime_id, user_id),
    CONSTRAINT fk_orders_showtime FOREIGN KEY (showtime_id) REFERENCES showtime (id)
);

CREATE TABLE order_seat
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    order_id   BIGINT      NOT NULL,
    seat_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_order_seat_order_seat UNIQUE (order_id, seat_id),
    CONSTRAINT fk_order_seat_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_seat_seat FOREIGN KEY (seat_id) REFERENCES seat (id)
);

ALTER TABLE seat
    ADD COLUMN current_order_id BIGINT      NULL AFTER status,
    ADD COLUMN held_until       DATETIME(6) NULL AFTER current_order_id,
    ADD CONSTRAINT fk_seat_current_order FOREIGN KEY (current_order_id) REFERENCES orders (id);

CREATE TABLE showtime_buyer
(
    showtime_id BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    PRIMARY KEY (showtime_id, user_id),
    CONSTRAINT fk_showtime_buyer_showtime FOREIGN KEY (showtime_id) REFERENCES showtime (id)
);
