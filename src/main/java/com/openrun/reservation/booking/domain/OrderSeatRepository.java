package com.openrun.reservation.booking.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSeatRepository extends JpaRepository<OrderSeat, Long> {
}
