package com.openrun.reservation.booking.api;

import com.openrun.reservation.booking.application.CreateOrderRequest;
import com.openrun.reservation.booking.application.OrderResponse;
import com.openrun.reservation.booking.application.OrderService;
import com.openrun.reservation.buyer.application.LoginBuyer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@AuthenticationPrincipal LoginBuyer buyer,
            @Valid @RequestBody CreateOrderRequest request) {
        return orderService.hold(buyer.id(), request);
    }

    @PostMapping("/{orderId}/confirm")
    public OrderResponse confirm(@AuthenticationPrincipal LoginBuyer buyer, @PathVariable Long orderId) {
        return orderService.confirm(buyer.id(), orderId);
    }
}
