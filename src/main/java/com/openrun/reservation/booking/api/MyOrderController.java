package com.openrun.reservation.booking.api;

import com.openrun.reservation.booking.application.MyOrdersResponse;
import com.openrun.reservation.booking.application.OrderService;
import com.openrun.reservation.buyer.application.LoginBuyer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MyOrderController {

    private final OrderService orderService;

    @GetMapping("/api/me/orders")
    public MyOrdersResponse myOrders(@AuthenticationPrincipal LoginBuyer buyer) {
        return orderService.findMyOrders(buyer.id());
    }
}
