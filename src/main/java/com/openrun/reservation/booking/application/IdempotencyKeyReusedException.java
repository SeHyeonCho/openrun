package com.openrun.reservation.booking.application;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class IdempotencyKeyReusedException extends ErrorResponseException {

    public IdempotencyKeyReusedException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, problemDetail(), null);
    }

    private static ProblemDetail problemDetail() {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                "같은 요청 식별자로 다른 내용의 선점을 보냈습니다.");
        problemDetail.setProperty("code", "IDEMPOTENCY_KEY_REUSED");
        return problemDetail;
    }
}
