package com.openrun.reservation.common.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ApiProblemException extends ErrorResponseException {

    private static final String CODE = "code";

    public ApiProblemException(HttpStatus status, String detail) {
        this(status, null, detail, Map.of());
    }

    public ApiProblemException(HttpStatus status, String code, String detail) {
        this(status, code, detail, Map.of());
    }

    public ApiProblemException(HttpStatus status, String code, String detail, Map<String, Object> properties) {
        super(status, problemDetail(status, code, detail, properties), null);
    }

    public static ApiProblemException badRequest(String detail) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, detail);
    }

    private static ProblemDetail problemDetail(HttpStatus status, String code, String detail,
            Map<String, Object> properties) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        if (code != null) {
            problemDetail.setProperty(CODE, code);
        }
        properties.forEach(problemDetail::setProperty);
        return problemDetail;
    }
}
