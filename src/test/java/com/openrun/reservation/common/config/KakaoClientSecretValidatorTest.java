package com.openrun.reservation.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

class KakaoClientSecretValidatorTest {

    @Test
    @DisplayName("카카오 Client Secret이 비었거나 공백뿐이면 앱 시작을 막는다")
    void rejectsBlankClientSecret() {
        assertThatThrownBy(() -> validatorWith("").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAKAO_CLIENT_SECRET");
        assertThatThrownBy(() -> validatorWith("   ").afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("카카오 Client Secret이 있으면 앱을 시작한다")
    void acceptsClientSecret() {
        assertThatCode(() -> validatorWith("secret").afterPropertiesSet()).doesNotThrowAnyException();
    }

    private KakaoClientSecretValidator validatorWith(String clientSecret) {
        ClientRegistration kakao = ClientRegistration.withRegistrationId("kakao")
                .clientId("client-id")
                .clientSecret(clientSecret)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .build();
        return new KakaoClientSecretValidator(new InMemoryClientRegistrationRepository(kakao));
    }
}
