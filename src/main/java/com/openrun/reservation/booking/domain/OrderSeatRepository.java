package com.openrun.reservation.booking.domain;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderSeatRepository extends JpaRepository<OrderSeat, Long> {

    @Query("""
            select count(os) from OrderSeat os
            where os.order.showtime.id = :showtimeId
              and os.order.userId = :userId
              and os.order.status = :status
              and os.order.heldUntil > :now
            """)
    long countHeldSeats(@Param("showtimeId") Long showtimeId, @Param("userId") Long userId,
            @Param("status") OrderStatus status, @Param("now") Instant now);
}
