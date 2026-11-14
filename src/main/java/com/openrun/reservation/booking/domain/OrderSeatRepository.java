package com.openrun.reservation.booking.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderSeatRepository extends JpaRepository<OrderSeat, Long> {

    @Query("""
            select count(os) from OrderSeat os
            where os.order.showtime.id = :showtimeId
              and os.order.buyerId = :buyerId
              and (os.order.status = com.openrun.reservation.booking.domain.OrderStatus.CONFIRMED
                or (os.order.status = com.openrun.reservation.booking.domain.OrderStatus.HELD
                  and os.order.heldUntil > :now))
            """)
    long countActiveSeats(@Param("showtimeId") Long showtimeId, @Param("buyerId") Long buyerId,
            @Param("now") Instant now);

    long countByOrderId(Long orderId);

    @Query("""
            select new com.openrun.reservation.booking.domain.OrderSeatCount(os.order.id, count(os))
            from OrderSeat os where os.order.id in :orderIds
            group by os.order.id
            """)
    List<OrderSeatCount> countByOrderIds(@Param("orderIds") List<Long> orderIds);

    @Query("select os.seat.id from OrderSeat os where os.order.id = :orderId order by os.seat.id")
    List<Long> findSeatIdsByOrderId(@Param("orderId") Long orderId);
}
