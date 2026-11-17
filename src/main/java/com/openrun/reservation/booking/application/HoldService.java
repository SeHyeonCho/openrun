package com.openrun.reservation.booking.application;

import java.util.Optional;
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
        Optional<HoldOutcome> outcome;
        try {
            outcome = orderService.hold(buyerId, idempotencyKey, request);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
            return orderService.replayRecordedHold(buyerId, idempotencyKey, request).orElseThrow(() -> e).orThrow();
        }
        return outcome.or(() -> orderService.replayRecordedHold(buyerId, idempotencyKey, request))
                .orElseThrow(() -> new IllegalStateException("선점 요청 기록을 다시 읽지 못했습니다."))
                .orThrow();
    }
}
