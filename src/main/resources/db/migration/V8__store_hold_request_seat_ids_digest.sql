ALTER TABLE hold_request
    ADD COLUMN seat_ids_digest CHAR(64) NULL AFTER quantity,
    MODIFY COLUMN rejection_seat_ids TEXT NULL;

UPDATE hold_request
SET seat_ids_digest = SHA2(seat_ids, 256)
WHERE seat_ids IS NOT NULL;

ALTER TABLE hold_request
    DROP CHECK ck_hold_request_seat_choice;

ALTER TABLE hold_request
    DROP COLUMN seat_ids,
    ADD CONSTRAINT ck_hold_request_seat_choice CHECK ((quantity IS NULL) <> (seat_ids_digest IS NULL));
