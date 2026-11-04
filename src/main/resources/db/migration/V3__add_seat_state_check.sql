ALTER TABLE seat
    ADD CONSTRAINT chk_seat_state CHECK (
        (status = 'HELD' AND current_order_id IS NOT NULL AND held_until IS NOT NULL)
        OR (status = 'SOLD' AND current_order_id IS NOT NULL AND held_until IS NULL)
        OR (status IN ('AVAILABLE', 'NOT_FOR_SALE') AND current_order_id IS NULL AND held_until IS NULL)
    );
