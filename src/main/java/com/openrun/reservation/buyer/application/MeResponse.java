package com.openrun.reservation.buyer.application;

import java.time.Instant;

public record MeResponse(String buyerId, String nickname, Instant serverTime) {

    public static MeResponse of(LoginBuyer buyer, Instant serverTime) {
        return new MeResponse(buyer.publicId(), buyer.nickname(), serverTime);
    }
}
