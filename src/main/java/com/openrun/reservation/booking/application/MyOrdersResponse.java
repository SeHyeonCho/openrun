package com.openrun.reservation.booking.application;

import com.openrun.reservation.booking.application.OrderResponse.PerformanceSummary;
import com.openrun.reservation.booking.application.OrderResponse.ShowtimeSummary;
import com.openrun.reservation.booking.domain.Order;
import com.openrun.reservation.booking.domain.OrderStatus;
import java.time.Instant;
import java.util.List;

public record MyOrdersResponse(List<MyOrder> orders, Instant serverTime) {

    public record MyOrder(
            String token,
            OrderStatus status,
            PerformanceSummary performance,
            ShowtimeSummary showtime,
            int quantity,
            List<Integer> entryNumbers,
            Instant heldUntil,
            Instant createdAt) {

        static MyOrder of(Order order, int quantity, Instant now) {
            return new MyOrder(
                    order.getToken(),
                    order.statusAt(now),
                    PerformanceSummary.of(order.getShowtime().getPerformance()),
                    ShowtimeSummary.of(order.getShowtime()),
                    quantity,
                    List.of(),
                    order.getHeldUntil(),
                    order.getCreatedAt());
        }
    }
}
