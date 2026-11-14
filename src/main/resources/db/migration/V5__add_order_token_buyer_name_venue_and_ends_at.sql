ALTER TABLE performance
    ADD COLUMN venue VARCHAR(200) NOT NULL DEFAULT '' AFTER title;
ALTER TABLE performance
    ALTER COLUMN venue DROP DEFAULT;

ALTER TABLE showtime
    ADD COLUMN ends_at DATETIME(6) NULL AFTER starts_at;
UPDATE showtime
SET ends_at = starts_at + INTERVAL 2 HOUR;
ALTER TABLE showtime
    MODIFY COLUMN ends_at DATETIME(6) NOT NULL,
    ADD CONSTRAINT ck_showtime_ends_after_start CHECK (starts_at < ends_at);

ALTER TABLE orders
    ADD COLUMN token      VARCHAR(40) NULL AFTER id,
    ADD COLUMN buyer_name VARCHAR(20) NOT NULL DEFAULT '' AFTER buyer_id;
UPDATE orders
SET token = CONCAT('o_', REPLACE(UUID(), '-', ''))
WHERE token IS NULL;
ALTER TABLE orders
    MODIFY COLUMN token VARCHAR(40) NOT NULL,
    ALTER COLUMN buyer_name DROP DEFAULT,
    ADD CONSTRAINT uk_orders_token UNIQUE (token),
    ADD INDEX idx_orders_buyer_created (buyer_id, created_at);
