package com.openrun.reservation.booking.domain;

import com.openrun.reservation.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldRequest extends BaseEntity {

    private static final String SEPARATOR = ",";
    private static final Duration RETENTION = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false, length = 36)
    private String idempotencyKey;

    @Column(nullable = false)
    private Long showtimeId;

    private Integer quantity;

    @Column(length = 1000)
    private String seatIds;

    @Column(nullable = false, length = 20)
    private String buyerName;

    private Long orderId;

    @Column(length = 40)
    private String rejectionCode;

    private String rejectionDetail;

    @Column(length = 1000)
    private String rejectionSeatIds;

    @Column(nullable = false)
    private Instant recordedAt;

    private HoldRequest(Long buyerId, String idempotencyKey, Long showtimeId, Integer quantity, String seatIds,
            String buyerName, Instant recordedAt) {
        this.buyerId = buyerId;
        this.idempotencyKey = idempotencyKey;
        this.showtimeId = showtimeId;
        this.quantity = quantity;
        this.seatIds = seatIds;
        this.buyerName = buyerName;
        this.recordedAt = recordedAt;
    }

    public static HoldRequest record(Long buyerId, String idempotencyKey, Long showtimeId, Integer quantity,
            Collection<Long> seatIds, String buyerName, Instant now) {
        return new HoldRequest(buyerId, idempotencyKey, showtimeId, quantity, join(seatIds), buyerName, now);
    }

    public boolean hasSameContent(Long showtimeId, Integer quantity, Collection<Long> seatIds, String buyerName) {
        return this.showtimeId.equals(showtimeId)
                && Objects.equals(this.quantity, quantity)
                && Objects.equals(this.seatIds, join(seatIds))
                && this.buyerName.equals(buyerName);
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(recordedAt.plus(RETENTION));
    }

    public void accept(Long orderId) {
        this.orderId = orderId;
    }

    public void reject(String code, String detail, List<Long> seatIds) {
        this.rejectionCode = code;
        this.rejectionDetail = detail;
        this.rejectionSeatIds = seatIds.isEmpty() ? null : join(seatIds);
    }

    public boolean isRejected() {
        return rejectionCode != null;
    }

    public List<Long> rejectedSeatIds() {
        if (rejectionSeatIds == null) {
            return List.of();
        }
        return Arrays.stream(rejectionSeatIds.split(SEPARATOR)).map(Long::valueOf).toList();
    }

    private static String join(Collection<Long> seatIds) {
        if (seatIds == null) {
            return null;
        }
        return seatIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(SEPARATOR));
    }
}
