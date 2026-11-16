package com.openrun.reservation.booking.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EntrySequence {

    private static final String ADVANCE = """
            INSERT INTO showtime_entry_sequence (showtime_id, last_number)
            VALUES (?, ?) AS incoming
            ON DUPLICATE KEY UPDATE last_number = showtime_entry_sequence.last_number + incoming.last_number
            """;
    private static final String LAST_NUMBER = """
            SELECT last_number FROM showtime_entry_sequence WHERE showtime_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public int reserve(Long showtimeId, int count) {
        jdbcTemplate.update(ADVANCE, showtimeId, count);
        int lastNumber = jdbcTemplate.queryForObject(LAST_NUMBER, Integer.class, showtimeId);
        return lastNumber - count + 1;
    }
}
