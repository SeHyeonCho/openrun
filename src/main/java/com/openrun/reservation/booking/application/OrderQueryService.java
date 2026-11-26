package com.openrun.reservation.booking.application;

import com.openrun.reservation.booking.domain.Order;
import com.openrun.reservation.booking.domain.OrderEntryNumberRepository;
import com.openrun.reservation.booking.domain.OrderEntryNumberView;
import com.openrun.reservation.booking.domain.OrderRepository;
import com.openrun.reservation.booking.domain.OrderSeatCount;
import com.openrun.reservation.booking.domain.OrderSeatRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderSeatRepository orderSeatRepository;
    private final OrderEntryNumberRepository orderEntryNumberRepository;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderResponse find(String token, Long viewerBuyerId) {
        Instant now = clock.instant();
        Order order = orderRepository.findWithShowtimeByToken(token).orElseThrow(OrderNotFoundException::new);
        return responseOf(order, viewerBuyerId, now);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MyOrdersResponse findMyOrders(Long buyerId) {
        Instant now = clock.instant();
        List<Order> orders = orderRepository.findAllWithShowtimeByBuyerId(buyerId);
        if (orders.isEmpty()) {
            return new MyOrdersResponse(List.of(), now);
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, Long> seatCounts = orderSeatRepository.countByOrderIds(orderIds).stream()
                .collect(Collectors.toMap(OrderSeatCount::orderId, OrderSeatCount::seatCount));
        Map<Long, List<Integer>> entryNumbers = orderEntryNumberRepository.findEntryNumbersByOrderIds(orderIds).stream()
                .collect(Collectors.groupingBy(OrderEntryNumberView::orderId,
                        Collectors.mapping(OrderEntryNumberView::entryNumber, Collectors.toList())));
        return new MyOrdersResponse(orders.stream()
                .map(order -> MyOrdersResponse.MyOrder.of(order, Math.toIntExact(seatCounts.get(order.getId())),
                        entryNumbers.getOrDefault(order.getId(), List.of()), now))
                .toList(), now);
    }

    OrderResponse responseOf(Order order, Long viewerBuyerId, Instant now) {
        int quantity = Math.toIntExact(orderSeatRepository.countByOrderId(order.getId()));
        return OrderResponse.of(order, quantity, orderEntryNumberRepository.findEntryNumbersByOrderId(order.getId()),
                viewerBuyerId, now);
    }
}
