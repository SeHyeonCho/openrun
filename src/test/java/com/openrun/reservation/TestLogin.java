package com.openrun.reservation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.openrun.reservation.buyer.application.LoginBuyer;
import java.util.UUID;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class TestLogin {

    private TestLogin() {
    }

    public static RequestPostProcessor loginAs(Long buyerId) {
        LoginBuyer buyer = new LoginBuyer(buyerId, publicIdOf(buyerId), "구매자" + buyerId);
        return authentication(new OAuth2AuthenticationToken(buyer, buyer.getAuthorities(), "kakao"));
    }

    public static RequestPostProcessor holdHeaders(Long buyerId) {
        return request -> {
            request.addHeader("Idempotency-Key", UUID.randomUUID().toString());
            request.addHeader("Expected-Buyer-Id", publicIdOf(buyerId));
            return request;
        };
    }

    public static String publicIdOf(Long buyerId) {
        return "b_test" + buyerId;
    }
}
