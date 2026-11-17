package com.openrun.reservation.booking.application;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HoldService {

    private final OrderService orderService;

    public OrderResponse hold(Long buyerId, UUID idempotencyKey, CreateOrderRequest request) {
        HoldOutcome outcome;
        try {
            outcome = orderService.hold(buyerId, idempotencyKey, request);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
            outcome = orderService.replayRecordedHold(buyerId, idempotencyKey, request).orElseThrow(() -> e);
        }
        return outcome.orThrow();
    }
}
