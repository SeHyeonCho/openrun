package com.openrun.reservation.booking.application;

import com.openrun.reservation.common.web.ApiProblemException;
import org.springframework.http.HttpStatus;

public class IdempotencyKeyReusedException extends ApiProblemException {

    public IdempotencyKeyReusedException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED", "같은 요청 식별자로 다른 내용의 선점을 보냈습니다.");
    }
}
