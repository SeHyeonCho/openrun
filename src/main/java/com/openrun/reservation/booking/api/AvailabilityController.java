package com.openrun.reservation.booking.api;

import com.openrun.reservation.booking.application.AvailabilityResponse;
import com.openrun.reservation.booking.application.PerformanceQueryService;
import com.openrun.reservation.buyer.application.LoginBuyer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/showtimes")
public class AvailabilityController {

    private final PerformanceQueryService performanceQueryService;

    @GetMapping("/{showtimeId}/availability")
    public AvailabilityResponse availability(@AuthenticationPrincipal LoginBuyer buyer, @PathVariable Long showtimeId) {
        return performanceQueryService.availability(showtimeId, buyer.id());
    }
}
