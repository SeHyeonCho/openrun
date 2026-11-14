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
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    @Column(nullable = false)
    private Long buyerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private Instant heldUntil;

    private Order(Showtime showtime, Long buyerId, OrderStatus status, Instant heldUntil) {
        this.showtime = showtime;
        this.buyerId = buyerId;
        this.status = status;
        this.heldUntil = heldUntil;
    }

    public static Order hold(Showtime showtime, Long buyerId, Instant now) {
        return new Order(showtime, buyerId, OrderStatus.HELD, showtime.holdDeadlineFrom(now));
    }

    public void expire() {
        if (status == OrderStatus.HELD) {
            this.status = OrderStatus.EXPIRED;
        }
    }

    public boolean isConfirmed() {
        return status == OrderStatus.CONFIRMED;
    }

    public boolean isConfirmableAt(Instant now) {
        return status == OrderStatus.HELD && now.isBefore(heldUntil);
    }

    public void confirm(Instant now) {
        if (isConfirmed()) {
            return;
        }
        if (!isConfirmableAt(now)) {
            throw new IllegalStateException("확정할 수 없는 주문이다: " + id);
        }
        this.status = OrderStatus.CONFIRMED;
    }
}
