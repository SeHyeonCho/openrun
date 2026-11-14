package com.openrun.reservation.booking.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("""
            select new com.openrun.reservation.booking.domain.OrderOwner(o.id, o.showtime.id, o.buyerId)
            from Order o where o.token = :token
            """)
    Optional<OrderOwner> findOwnerByToken(@Param("token") String token);

    @Query("""
            select o from Order o join fetch o.showtime s join fetch s.performance
            where o.token = :token
            """)
    Optional<Order> findWithShowtimeByToken(@Param("token") String token);

    @Query("""
            select o from Order o join fetch o.showtime s join fetch s.performance
            where o.buyerId = :buyerId
            order by o.createdAt desc, o.id desc
            """)
    List<Order> findAllWithShowtimeByBuyerId(@Param("buyerId") Long buyerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
