package com.openrun.reservation.buyer.application;

import java.util.Map;

public record KakaoAccount(Long kakaoId, String nickname) {

    private static final String NO_NICKNAME = "";

    public static KakaoAccount from(Map<String, Object> attributes) {
        if (!(attributes.get("id") instanceof Number id)) {
            throw new IllegalArgumentException("카카오 사용자 정보에 계정 식별값이 없다");
        }
        return new KakaoAccount(id.longValue(), nicknameOf(attributes));
    }

    private static String nicknameOf(Map<String, Object> attributes) {
        if (attributes.get("kakao_account") instanceof Map<?, ?> account
                && account.get("profile") instanceof Map<?, ?> profile
                && profile.get("nickname") instanceof String nickname) {
            return nickname;
        }
        if (attributes.get("properties") instanceof Map<?, ?> properties
                && properties.get("nickname") instanceof String nickname) {
            return nickname;
        }
        return NO_NICKNAME;
    }
}
