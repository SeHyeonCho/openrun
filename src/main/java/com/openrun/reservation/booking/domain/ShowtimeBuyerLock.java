package com.openrun.reservation.booking.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShowtimeBuyerLock {

    private static final String ACQUIRE = """
            INSERT INTO showtime_buyer (showtime_id, buyer_id)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE buyer_id = buyer_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public void acquire(Long showtimeId, Long buyerId) {
        jdbcTemplate.update(ACQUIRE, showtimeId, buyerId);
    }
}
