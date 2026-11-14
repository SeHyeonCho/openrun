CREATE TABLE buyer
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    public_id  VARCHAR(32)  NOT NULL,
    kakao_id   BIGINT       NOT NULL,
    nickname   VARCHAR(100) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_buyer_public_id UNIQUE (public_id),
    CONSTRAINT uk_buyer_kakao_id UNIQUE (kakao_id)
);

ALTER TABLE orders
    RENAME COLUMN user_id TO buyer_id,
    RENAME INDEX idx_orders_showtime_user TO idx_orders_showtime_buyer;

ALTER TABLE showtime_buyer
    RENAME COLUMN user_id TO buyer_id;
