CREATE TABLE performance
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    title      VARCHAR(200) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE showtime
(
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    performance_id     BIGINT      NOT NULL,
    starts_at          DATETIME(6) NOT NULL,
    sales_open_at      DATETIME(6) NOT NULL,
    hold_minutes       INT         NOT NULL,
    max_seats_per_user INT         NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_showtime_performance FOREIGN KEY (performance_id) REFERENCES performance (id),
    CONSTRAINT ck_showtime_sales_open_before_start CHECK (sales_open_at < starts_at),
    CONSTRAINT ck_showtime_hold_minutes CHECK (hold_minutes > 0),
    CONSTRAINT ck_showtime_max_seats_per_user CHECK (max_seats_per_user > 0)
);

CREATE TABLE seat
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    showtime_id BIGINT      NOT NULL,
    type        VARCHAR(20) NOT NULL,
    label       VARCHAR(20) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_seat_showtime_label UNIQUE (showtime_id, label),
    CONSTRAINT fk_seat_showtime FOREIGN KEY (showtime_id) REFERENCES showtime (id)
);
