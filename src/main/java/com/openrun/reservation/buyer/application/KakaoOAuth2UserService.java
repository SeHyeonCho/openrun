package com.openrun.reservation.buyer.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KakaoOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> kakaoUserInfoClient = new DefaultOAuth2UserService();
    private final BuyerService buyerService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User kakaoUser = kakaoUserInfoClient.loadUser(request);
        return login(KakaoAccount.from(kakaoUser.getAttributes()));
    }

    public LoginBuyer login(KakaoAccount account) {
        try {
            return buyerService.loginWithKakao(account);
        } catch (DataIntegrityViolationException e) {
            return buyerService.loginWithKakao(account);
        }
    }
}
