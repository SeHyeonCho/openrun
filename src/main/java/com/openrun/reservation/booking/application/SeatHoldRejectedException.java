package com.openrun.reservation.booking.application;

import com.openrun.reservation.common.web.ApiProblemException;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class SeatHoldRejectedException extends ApiProblemException {

    private final SeatHoldRejection rejection;
    private final List<Long> seatIds;

    public SeatHoldRejectedException(SeatHoldRejection rejection, String detail) {
        this(rejection, detail, List.of());
    }

    public SeatHoldRejectedException(SeatHoldRejection rejection, String detail, List<Long> seatIds) {
        super(HttpStatus.CONFLICT, rejection.name(), detail,
                seatIds.isEmpty() ? Map.of() : Map.of("seatIds", List.copyOf(seatIds)));
        this.rejection = rejection;
        this.seatIds = List.copyOf(seatIds);
    }
}
