package com.openrun.reservation.common.token;

import java.security.SecureRandom;
import java.util.Base64;

public final class RandomToken {

    private static final int RANDOM_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private RandomToken() {
    }

    public static String generate(String prefix) {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return prefix + ENCODER.encodeToString(bytes);
    }
}
