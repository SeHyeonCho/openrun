package com.openrun.reservation.booking.domain;

import com.openrun.reservation.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatType type;

    @Column(nullable = false, length = 20)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_order_id")
    private Order currentOrder;

    private Instant heldUntil;

    private Seat(Showtime showtime, SeatType type, String label, SeatStatus status) {
        this.showtime = showtime;
        this.type = type;
        this.label = label;
        this.status = status;
    }

    public static Seat forSale(Showtime showtime, SeatType type, String label) {
        return new Seat(showtime, type, label, SeatStatus.AVAILABLE);
    }

    public static Seat notForSale(Showtime showtime, SeatType type, String label) {
        return new Seat(showtime, type, label, SeatStatus.NOT_FOR_SALE);
    }

    public boolean isAvailableAt(Instant now) {
        return status == SeatStatus.AVAILABLE || isHoldExpiredAt(now);
    }

    public boolean isHoldExpiredAt(Instant now) {
        return status == SeatStatus.HELD && !now.isBefore(heldUntil);
    }

    public SeatStatus statusAt(Instant now) {
        return isHoldExpiredAt(now) ? SeatStatus.AVAILABLE : status;
    }

    public void hold(Order order, Instant now) {
        if (!isAvailableAt(now)) {
            throw new IllegalStateException("선점할 수 없는 좌석이다: " + label);
        }
        this.status = SeatStatus.HELD;
        this.currentOrder = order;
        this.heldUntil = order.getHeldUntil();
    }

    public void confirm(Order order) {
        if (status != SeatStatus.HELD || currentOrder != order) {
            throw new IllegalStateException("이 주문이 선점한 좌석이 아니다: " + label);
        }
        this.status = SeatStatus.SOLD;
        this.heldUntil = null;
    }
}
