package com.openrun.reservation.buyer.api;

import com.openrun.reservation.buyer.application.LoginBuyer;
import com.openrun.reservation.buyer.application.MeResponse;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MeController {

    private final Clock clock;

    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal LoginBuyer buyer) {
        return MeResponse.of(buyer, clock.instant());
    }
}
