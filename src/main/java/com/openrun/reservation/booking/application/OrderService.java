package com.openrun.reservation.booking.application;

import com.openrun.reservation.booking.domain.EntrySequence;
import com.openrun.reservation.booking.domain.HoldRequest;
import com.openrun.reservation.booking.domain.HoldRequestRepository;
import com.openrun.reservation.booking.domain.Order;
import com.openrun.reservation.booking.domain.OrderEntryNumber;
import com.openrun.reservation.booking.domain.OrderEntryNumberRepository;
import com.openrun.reservation.booking.domain.OrderEntryNumberView;
import com.openrun.reservation.booking.domain.OrderOwner;
import com.openrun.reservation.booking.domain.OrderRepository;
import com.openrun.reservation.booking.domain.OrderSeat;
import com.openrun.reservation.booking.domain.OrderSeatCount;
import com.openrun.reservation.booking.domain.OrderSeatRepository;
import com.openrun.reservation.booking.domain.Seat;
import com.openrun.reservation.booking.domain.SeatRepository;
import com.openrun.reservation.booking.domain.Showtime;
import com.openrun.reservation.booking.domain.ShowtimeBuyerLock;
import com.openrun.reservation.booking.domain.ShowtimeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
    private final OrderEntryNumberRepository orderEntryNumberRepository;
    private final EntrySequence entrySequence;
    private final HoldRequestRepository holdRequestRepository;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<HoldOutcome> hold(Long buyerId, UUID idempotencyKey, CreateOrderRequest request) {
        rejectInvalidSeatChoice(request);
        Instant now = clock.instant();
        String key = idempotencyKey.toString();
        Optional<HoldRequest> recorded = holdRequestRepository.findByBuyerIdAndIdempotencyKey(buyerId, key);
        if (recorded.isPresent()) {
            if (!recorded.get().isExpiredAt(now)) {
                return Optional.empty();
            }
            holdRequestRepository.delete(recorded.get());
            holdRequestRepository.flush();
        }

        Showtime showtime = showtimeRepository.findById(request.showtimeId())
                .orElseThrow(() -> new ShowtimeNotFoundException(request.showtimeId()));
        if (showtime.isGeneralAdmission() != request.choosesQuantity()) {
            throw badRequest(showtime.isGeneralAdmission()
                    ? "자유석 회차는 좌석 대신 수량(quantity)으로 선점합니다."
                    : "지정석 회차는 수량 대신 좌석(seatIds)으로 선점합니다.");
        }

        HoldRequest holdRequest = holdRequestRepository.saveAndFlush(HoldRequest.record(buyerId, key,
                request.showtimeId(), request.quantity(), request.seatIds(), request.buyerName(), now));
        try {
            Order order = holdSeats(buyerId, showtime, request, now);
            holdRequest.accept(order.getId());
            return Optional.of(HoldOutcome.accepted(
                    OrderResponse.of(order, request.requestedSeatCount(), List.of(), buyerId, now)));
        } catch (SeatHoldRejectedException rejection) {
            holdRequest.reject(rejection.getRejection().name(), rejection.getBody().getDetail(),
                    rejection.getSeatIds());
            return Optional.of(HoldOutcome.rejected(rejection));
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<HoldOutcome> replayRecordedHold(Long buyerId, UUID idempotencyKey, CreateOrderRequest request) {
        Instant now = clock.instant();
        return holdRequestRepository.findByBuyerIdAndIdempotencyKey(buyerId, idempotencyKey.toString())
                .map(recorded -> replay(recorded, request, buyerId, now));
    }

    private HoldOutcome replay(HoldRequest recorded, CreateOrderRequest request, Long buyerId, Instant now) {
        if (!recorded.hasSameContent(request.showtimeId(), request.quantity(), request.seatIds(),
                request.buyerName())) {
            throw new IdempotencyKeyReusedException();
        }
        if (recorded.isRejected()) {
            return HoldOutcome.rejected(new SeatHoldRejectedException(
                    SeatHoldRejection.valueOf(recorded.getRejectionCode()), recorded.getRejectionDetail(),
                    recorded.rejectedSeatIds()));
        }
        Order order = orderRepository.findWithShowtimeById(recorded.getOrderId()).orElseThrow(OrderNotFoundException::new);
        return HoldOutcome.accepted(orderResponse(order, buyerId, now));
    }

    private Order holdSeats(Long buyerId, Showtime showtime, CreateOrderRequest request, Instant now) {
        if (!showtime.isSalesOpenAt(now)) {
            throw new SeatHoldRejectedException(SeatHoldRejection.SALES_NOT_OPEN, "아직 판매가 시작되지 않았습니다.");
        }

        showtimeBuyerLock.acquire(showtime.getId(), buyerId);
        long activeSeats = orderSeatRepository.countActiveSeats(showtime.getId(), buyerId, now);
        if (activeSeats + request.requestedSeatCount() > showtime.getMaxSeatsPerUser()) {
            throw new SeatHoldRejectedException(SeatHoldRejection.SEAT_LIMIT_EXCEEDED,
                    "1인 최대 좌석 수(" + showtime.getMaxSeatsPerUser() + "석)를 넘습니다.");
        }

        List<Seat> seats = request.choosesQuantity()
                ? lockGeneralSeats(showtime, request.quantity(), now)
                : lockReservedSeats(showtime, request.seatIds(), now);
        expirePreviousOrders(seats, now);

        Order order = orderRepository.save(Order.hold(showtime, buyerId, request.buyerName(), now));
        seats.forEach(seat -> seat.hold(order, now));
        orderSeatRepository.saveAll(seats.stream().map(seat -> OrderSeat.of(order, seat)).toList());
        return order;
    }

    private OrderResponse orderResponse(Order order, Long viewerBuyerId, Instant now) {
        int quantity = Math.toIntExact(orderSeatRepository.countByOrderId(order.getId()));
        return OrderResponse.of(order, quantity, orderEntryNumberRepository.findEntryNumbersByOrderId(order.getId()),
                viewerBuyerId, now);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderResponse find(String token, Long viewerBuyerId) {
        Order order = orderRepository.findWithShowtimeByToken(token).orElseThrow(OrderNotFoundException::new);
        return orderResponse(order, viewerBuyerId, clock.instant());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MyOrdersResponse findMyOrders(Long buyerId) {
        List<Order> orders = orderRepository.findAllWithShowtimeByBuyerId(buyerId);
        Instant now = clock.instant();
        if (orders.isEmpty()) {
            return new MyOrdersResponse(List.of(), now);
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, Long> seatCounts = orderSeatRepository.countByOrderIds(orderIds).stream()
                .collect(Collectors.toMap(OrderSeatCount::orderId, OrderSeatCount::seatCount));
        Map<Long, List<Integer>> entryNumbers = orderEntryNumberRepository.findEntryNumbersByOrderIds(orderIds).stream()
                .collect(Collectors.groupingBy(OrderEntryNumberView::orderId,
                        Collectors.mapping(OrderEntryNumberView::entryNumber, Collectors.toList())));
        return new MyOrdersResponse(orders.stream()
                .map(order -> MyOrdersResponse.MyOrder.of(order, Math.toIntExact(seatCounts.get(order.getId())),
                        entryNumbers.getOrDefault(order.getId(), List.of()), now))
                .toList(), now);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse confirm(Long buyerId, String token) {
        OrderOwner owner = orderRepository.findOwnerByToken(token)
                .filter(found -> found.buyerId().equals(buyerId))
                .orElseThrow(OrderNotFoundException::new);
        Long orderId = owner.orderId();

        showtimeBuyerLock.acquire(owner.showtimeId(), owner.buyerId());
        List<Seat> seats = seatRepository.findAllByIdForUpdate(orderSeatRepository.findSeatIdsByOrderId(orderId));
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(OrderNotFoundException::new);
        Instant now = clock.instant();

        if (!order.isConfirmed()) {
            if (!order.isConfirmableAt(now)) {
                throw new OrderConfirmRejectedException(OrderConfirmRejection.ORDER_EXPIRED, "선점 기한이 지난 주문입니다.");
            }
            order.confirm(now);
            seats.forEach(seat -> seat.confirm(order));
            assignEntryNumbers(order, seats.size());
        }
        return OrderResponse.of(order, seats.size(), orderEntryNumberRepository.findEntryNumbersByOrderId(orderId),
                buyerId, now);
    }

    private void assignEntryNumbers(Order order, int quantity) {
        Showtime showtime = order.getShowtime();
        if (!showtime.isGeneralAdmission()) {
            return;
        }
        int first = entrySequence.reserve(showtime.getId(), quantity);
        orderEntryNumberRepository.saveAll(IntStream.range(first, first + quantity)
                .mapToObj(number -> OrderEntryNumber.of(order, number))
                .toList());
    }

    private void rejectInvalidSeatChoice(CreateOrderRequest request) {
        List<Long> seatIds = request.seatIds();
        if ((seatIds == null) == (request.quantity() == null)) {
            throw badRequest("좌석(seatIds)과 수량(quantity) 가운데 하나만 보내야 합니다.");
        }
        if (seatIds != null && (seatIds.isEmpty() || new HashSet<>(seatIds).size() != seatIds.size())) {
            throw badRequest("좌석 목록이 비었거나 같은 좌석이 두 번 있습니다.");
        }
    }

    private List<Seat> lockGeneralSeats(Showtime showtime, int quantity, Instant now) {
        List<Seat> seats = seatRepository.findHoldableGeneralSeatsForUpdate(showtime.getId(), now, quantity);
        if (seats.size() < quantity) {
            throw new SeatHoldRejectedException(SeatHoldRejection.SEAT_NOT_AVAILABLE, "남은 좌석이 요청한 수량보다 적습니다.");
        }
        return seats;
    }

    private List<Seat> lockReservedSeats(Showtime showtime, List<Long> seatIds, Instant now) {
        List<Seat> seats = seatRepository.findAllByIdForUpdate(seatIds);
        rejectSeatsOutsideShowtime(showtime, seatIds, seats);
        rejectUnavailableSeats(seats, now);
        return seats;
    }

    private ErrorResponseException badRequest(String detail) {
        return new ErrorResponseException(HttpStatus.BAD_REQUEST,
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail), null);
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
