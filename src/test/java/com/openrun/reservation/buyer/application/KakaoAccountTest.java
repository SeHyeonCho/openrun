package com.openrun.reservation.buyer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KakaoAccountTest {

    @Test
    @DisplayName("카카오 사용자 정보에서 계정 식별값과 프로필 닉네임을 읽는다")
    void readsIdAndProfileNickname() {
        KakaoAccount account = KakaoAccount.from(Map.of(
                "id", 3_141_592_653L,
                "kakao_account", Map.of("profile", Map.of("nickname", "밤산책"))));

        assertThat(account).isEqualTo(new KakaoAccount(3_141_592_653L, "밤산책"));
    }

    @Test
    @DisplayName("프로필 닉네임이 없으면 properties의 닉네임을 쓰고, 둘 다 없으면 빈 문자열로 둔다")
    void fallsBackToPropertiesNickname() {
        assertThat(KakaoAccount.from(Map.of("id", 1L, "properties", Map.of("nickname", "새벽"))).nickname())
                .isEqualTo("새벽");
        assertThat(KakaoAccount.from(Map.of("id", 1L)).nickname()).isEmpty();
    }

    @Test
    @DisplayName("계정 식별값이 없으면 로그인을 거부한다")
    void rejectsMissingId() {
        assertThatThrownBy(() -> KakaoAccount.from(Map.of("properties", Map.of("nickname", "새벽"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
