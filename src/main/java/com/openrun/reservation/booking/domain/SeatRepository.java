package com.openrun.reservation.booking.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByShowtimeIdOrderByIdAsc(Long showtimeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id in :ids order by s.id")
    List<Seat> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Query(value = """
            SELECT * FROM seat
            WHERE showtime_id = :showtimeId
              AND type = 'GENERAL'
              AND (status = 'AVAILABLE' OR (status = 'HELD' AND held_until <= :now))
            ORDER BY id
            LIMIT :count
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Seat> findHoldableGeneralSeatsForUpdate(@Param("showtimeId") Long showtimeId, @Param("now") Instant now,
            @Param("count") int count);
}
