ALTER TABLE showtime
    ADD COLUMN sales_close_at DATETIME(6) NULL AFTER sales_open_at;

UPDATE showtime
SET sales_close_at = ends_at;

ALTER TABLE showtime
    MODIFY COLUMN sales_close_at DATETIME(6) NOT NULL,
    ADD CONSTRAINT ck_showtime_sales_close_after_open CHECK (sales_open_at < sales_close_at),
    ADD CONSTRAINT ck_showtime_sales_close_until_end CHECK (sales_close_at <= ends_at);
