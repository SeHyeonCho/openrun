package com.openrun.reservation.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
class KakaoClientSecretValidator implements InitializingBean {

    private static final String KAKAO = "kakao";

    private final ClientRegistrationRepository clientRegistrations;

    @Override
    public void afterPropertiesSet() {
        ClientRegistration kakao = clientRegistrations.findByRegistrationId(KAKAO);
        if (kakao == null || !StringUtils.hasText(kakao.getClientSecret())) {
            throw new IllegalStateException("카카오 로그인의 KAKAO_CLIENT_SECRET이 비어 있다");
        }
    }
}
