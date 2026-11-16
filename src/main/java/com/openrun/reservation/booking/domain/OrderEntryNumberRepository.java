package com.openrun.reservation.booking.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderEntryNumberRepository extends JpaRepository<OrderEntryNumber, Long> {

    @Query("""
            select e.entryNumber from OrderEntryNumber e
            where e.order.id = :orderId
            order by e.entryNumber
            """)
    List<Integer> findEntryNumbersByOrderId(@Param("orderId") Long orderId);

    @Query("""
            select new com.openrun.reservation.booking.domain.OrderEntryNumberView(e.order.id, e.entryNumber)
            from OrderEntryNumber e
            where e.order.id in :orderIds
            order by e.order.id, e.entryNumber
            """)
    List<OrderEntryNumberView> findEntryNumbersByOrderIds(@Param("orderIds") Collection<Long> orderIds);
}
