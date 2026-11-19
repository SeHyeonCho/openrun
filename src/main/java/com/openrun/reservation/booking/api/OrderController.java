package com.openrun.reservation.booking.api;

import com.openrun.reservation.booking.application.CreateOrderRequest;
import com.openrun.reservation.booking.application.HoldService;
import com.openrun.reservation.booking.application.OrderResponse;
import com.openrun.reservation.booking.application.OrderService;
import com.openrun.reservation.booking.application.SeatHoldRejectedException;
import com.openrun.reservation.booking.application.SeatHoldRejection;
import com.openrun.reservation.buyer.application.LoginBuyer;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String EXPECTED_BUYER_ID = "Expected-Buyer-Id";
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final OrderService orderService;
    private final HoldService holdService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@AuthenticationPrincipal LoginBuyer buyer,
            @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(EXPECTED_BUYER_ID) String expectedBuyerId,
            @Valid @RequestBody CreateOrderRequest request) {
        UUID key = parseIdempotencyKey(idempotencyKey);
        if (!buyer.publicId().equals(expectedBuyerId)) {
            throw new SeatHoldRejectedException(SeatHoldRejection.BUYER_CHANGED,
                    "요청을 만든 구매자와 지금 로그인한 구매자가 다릅니다.");
        }
        return holdService.hold(buyer.id(), key, request);
    }

    @GetMapping("/{token}")
    public OrderResponse find(@AuthenticationPrincipal LoginBuyer buyer, @PathVariable String token) {
        return orderService.find(token, buyer == null ? null : buyer.id());
    }

    @PostMapping("/{token}/confirm")
    public OrderResponse confirm(@AuthenticationPrincipal LoginBuyer buyer, @PathVariable String token) {
        return orderService.confirm(buyer.id(), token);
    }

    @PostMapping("/{token}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal LoginBuyer buyer, @PathVariable String token) {
        return orderService.cancel(buyer.id(), token);
    }

    private UUID parseIdempotencyKey(String value) {
        if (!UUID_PATTERN.matcher(value).matches()) {
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, IDEMPOTENCY_KEY + " 헤더는 UUID 문자열이어야 합니다."), null);
        }
        return UUID.fromString(value);
    }
}
