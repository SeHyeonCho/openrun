package com.openrun.reservation.booking.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldRequestRepository extends JpaRepository<HoldRequest, Long> {

    Optional<HoldRequest> findByBuyerIdAndIdempotencyKey(Long buyerId, String idempotencyKey);
}
