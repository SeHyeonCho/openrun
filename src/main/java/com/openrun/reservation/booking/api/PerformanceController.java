package com.openrun.reservation.booking.api;

import com.openrun.reservation.booking.application.PerformanceQueryService;
import com.openrun.reservation.booking.application.PerformanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/performances")
public class PerformanceController {

    private final PerformanceQueryService performanceQueryService;

    @GetMapping("/{performanceId}")
    public PerformanceResponse find(@PathVariable Long performanceId) {
        return performanceQueryService.find(performanceId);
    }
}
