package com.openrun.reservation.buyer.domain;

import com.openrun.reservation.common.entity.BaseEntity;
import com.openrun.reservation.common.token.RandomToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Buyer extends BaseEntity {

    private static final String PUBLIC_ID_PREFIX = "b_";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32, unique = true)
    private String publicId;

    @Column(nullable = false, unique = true)
    private Long kakaoId;

    @Column(nullable = false, length = 100)
    private String nickname;

    private Buyer(String publicId, Long kakaoId, String nickname) {
        this.publicId = publicId;
        this.kakaoId = kakaoId;
        this.nickname = nickname;
    }

    public static Buyer registerWithKakao(Long kakaoId, String nickname) {
        return new Buyer(RandomToken.generate(PUBLIC_ID_PREFIX), kakaoId, nickname);
    }

    public void refreshNickname(String nickname) {
        this.nickname = nickname;
    }
}
