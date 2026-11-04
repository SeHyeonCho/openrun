package com.openrun.reservation.booking.api;

import com.openrun.reservation.booking.application.ConfirmOrderRequest;
import com.openrun.reservation.booking.application.CreateOrderRequest;
import com.openrun.reservation.booking.application.OrderResponse;
import com.openrun.reservation.booking.application.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.hold(request);
    }

    @PostMapping("/{orderId}/confirm")
    public OrderResponse confirm(@PathVariable Long orderId, @Valid @RequestBody ConfirmOrderRequest request) {
        return orderService.confirm(orderId, request);
    }
}
