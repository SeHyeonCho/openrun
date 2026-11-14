package com.openrun.reservation.booking.application;

import com.openrun.reservation.booking.domain.Order;
import com.openrun.reservation.booking.domain.OrderStatus;
import com.openrun.reservation.booking.domain.Performance;
import com.openrun.reservation.booking.domain.Showtime;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String token,
        OrderStatus status,
        PerformanceSummary performance,
        ShowtimeSummary showtime,
        int quantity,
        List<Integer> entryNumbers,
        String buyerName,
        Instant heldUntil,
        Instant createdAt,
        boolean isOwner,
        Instant serverTime) {

    public static OrderResponse of(Order order, int quantity, Long viewerBuyerId, Instant now) {
        return new OrderResponse(
                order.getToken(),
                order.statusAt(now),
                PerformanceSummary.of(order.getShowtime().getPerformance()),
                ShowtimeSummary.of(order.getShowtime()),
                quantity,
                List.of(),
                order.getBuyerName(),
                order.getHeldUntil(),
                order.getCreatedAt(),
                viewerBuyerId != null && order.isOwnedBy(viewerBuyerId),
                now);
    }

    public record PerformanceSummary(Long id, String title, String venue) {

        static PerformanceSummary of(Performance performance) {
            return new PerformanceSummary(performance.getId(), performance.getTitle(), performance.getVenue());
        }
    }

    public record ShowtimeSummary(Long id, Instant startsAt, Instant endsAt) {

        static ShowtimeSummary of(Showtime showtime) {
            return new ShowtimeSummary(showtime.getId(), showtime.getStartsAt(), showtime.getEndsAt());
        }
    }
}
