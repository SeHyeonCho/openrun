package com.openrun.reservation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.openrun.reservation.buyer.application.LoginBuyer;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class TestLogin {

    private TestLogin() {
    }

    public static RequestPostProcessor loginAs(Long buyerId) {
        LoginBuyer buyer = new LoginBuyer(buyerId, "b_test" + buyerId, "구매자" + buyerId);
        return authentication(new OAuth2AuthenticationToken(buyer, buyer.getAuthorities(), "kakao"));
    }
}
