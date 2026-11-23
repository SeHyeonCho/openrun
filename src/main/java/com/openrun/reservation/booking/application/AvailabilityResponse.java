package com.openrun.reservation.booking.application;

import com.openrun.reservation.booking.domain.SalesStatus;
import java.time.Instant;

public record AvailabilityResponse(
        Long showtimeId,
        SalesStatus salesStatus,
        long remaining,
        int maxSeatsPerBuyer,
        long ownedCount,
        Instant serverTime) {
}
