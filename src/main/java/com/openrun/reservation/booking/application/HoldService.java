package com.openrun.reservation.booking.application;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HoldService {

    private final OrderService orderService;

    public OrderResponse hold(Long buyerId, UUID idempotencyKey, CreateOrderRequest request) {
        return orderService.hold(buyerId, idempotencyKey, request).orThrow();
    }
}
