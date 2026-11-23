package com.openrun.reservation.booking.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    List<Showtime> findAllByPerformanceIdOrderByStartsAtAscIdAsc(Long performanceId);

    boolean existsBySalesCloseAtAfter(Instant now);
}
