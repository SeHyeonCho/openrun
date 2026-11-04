package com.openrun.reservation.booking.application;

import com.openrun.reservation.booking.domain.Order;
import com.openrun.reservation.booking.domain.OrderRepository;
import com.openrun.reservation.booking.domain.OrderSeat;
import com.openrun.reservation.booking.domain.OrderSeatRepository;
import com.openrun.reservation.booking.domain.OrderStatus;
import com.openrun.reservation.booking.domain.Seat;
import com.openrun.reservation.booking.domain.SeatRepository;
import com.openrun.reservation.booking.domain.Showtime;
import com.openrun.reservation.booking.domain.ShowtimeBuyerLock;
import com.openrun.reservation.booking.domain.ShowtimeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final OrderRepository orderRepository;
    private final OrderSeatRepository orderSeatRepository;
    private final ShowtimeBuyerLock showtimeBuyerLock;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse hold(CreateOrderRequest request) {
        List<Long> seatIds = request.seatIds();
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST,
                    ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "같은 좌석을 두 번 요청할 수 없습니다."), null);
        }
        Showtime showtime = showtimeRepository.findById(request.showtimeId())
                .orElseThrow(() -> new ShowtimeNotFoundException(request.showtimeId()));
        Instant now = clock.instant();
        if (!showtime.isSalesOpenAt(now)) {
            throw new SeatHoldRejectedException(SeatHoldRejection.SALES_NOT_OPEN, "아직 판매가 시작되지 않았습니다.");
        }

        showtimeBuyerLock.acquire(showtime.getId(), request.userId());
        long heldSeats = orderSeatRepository.countHeldSeats(showtime.getId(), request.userId(), OrderStatus.HELD, now);
        if (heldSeats + seatIds.size() > showtime.getMaxSeatsPerUser()) {
            throw new SeatHoldRejectedException(SeatHoldRejection.SEAT_LIMIT_EXCEEDED,
                    "1인 최대 좌석 수(" + showtime.getMaxSeatsPerUser() + "석)를 넘습니다.");
        }

        List<Seat> seats = seatRepository.findAllByIdForUpdate(seatIds);
        rejectSeatsOutsideShowtime(showtime, seatIds, seats);
        rejectUnavailableSeats(seats, now);
        expirePreviousOrders(seats, now);

        Order order = orderRepository.save(Order.hold(showtime, request.userId(), now));
        seats.forEach(seat -> seat.hold(order, now));
        orderSeatRepository.saveAll(seats.stream().map(seat -> OrderSeat.of(order, seat)).toList());
        return OrderResponse.of(order, showtime.getId(), seats);
    }

    private void rejectSeatsOutsideShowtime(Showtime showtime, List<Long> seatIds, List<Seat> seats) {
        Set<Long> inShowtime = new HashSet<>();
        for (Seat seat : seats) {
            if (seat.getShowtime().getId().equals(showtime.getId())) {
                inShowtime.add(seat.getId());
            }
        }
        List<Long> outside = seatIds.stream().filter(id -> !inShowtime.contains(id)).sorted().toList();
        if (!outside.isEmpty()) {
            throw new SeatHoldRejectedException(SeatHoldRejection.SEAT_NOT_IN_SHOWTIME,
                    "이 회차에 없는 좌석이 있습니다.", outside);
        }
    }

    private void rejectUnavailableSeats(List<Seat> seats, Instant now) {
        List<Long> unavailable = seats.stream().filter(seat -> !seat.isAvailableAt(now)).map(Seat::getId).toList();
        if (!unavailable.isEmpty()) {
            throw new SeatHoldRejectedException(SeatHoldRejection.SEAT_NOT_AVAILABLE,
                    "선점할 수 없는 좌석이 있습니다.", unavailable);
        }
    }

    private void expirePreviousOrders(List<Seat> seats, Instant now) {
        seats.stream()
                .filter(seat -> seat.isHoldExpiredAt(now))
                .map(Seat::getCurrentOrder)
                .distinct()
                .forEach(Order::expire);
    }
}
