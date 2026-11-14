package com.openrun.reservation.booking.domain;

import com.openrun.reservation.common.entity.BaseEntity;
import com.openrun.reservation.common.token.RandomToken;
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

    private static final String TOKEN_PREFIX = "o_";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false, length = 20)
    private String buyerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private Instant heldUntil;

    private Order(String token, Showtime showtime, Long buyerId, String buyerName, OrderStatus status,
            Instant heldUntil) {
        this.token = token;
        this.showtime = showtime;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
        this.status = status;
        this.heldUntil = heldUntil;
    }

    public static Order hold(Showtime showtime, Long buyerId, String buyerName, Instant now) {
        return new Order(RandomToken.generate(TOKEN_PREFIX), showtime, buyerId, buyerName, OrderStatus.HELD,
                showtime.holdDeadlineFrom(now));
    }

    public OrderStatus statusAt(Instant now) {
        if (status == OrderStatus.HELD && !now.isBefore(heldUntil)) {
            return OrderStatus.EXPIRED;
        }
        return status;
    }

    public boolean isOwnedBy(Long buyerId) {
        return this.buyerId.equals(buyerId);
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
