package com.openrun.reservation.booking.domain;

import com.openrun.reservation.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
public class Showtime extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performance_id", nullable = false)
    private Performance performance;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant salesOpenAt;

    @Column(nullable = false)
    private int holdMinutes;

    @Column(nullable = false)
    private int maxSeatsPerUser;

    private Showtime(Performance performance, Instant startsAt, Instant salesOpenAt, int holdMinutes,
            int maxSeatsPerUser) {
        this.performance = performance;
        this.startsAt = startsAt;
        this.salesOpenAt = salesOpenAt;
        this.holdMinutes = holdMinutes;
        this.maxSeatsPerUser = maxSeatsPerUser;
    }

    public static Showtime create(Performance performance, Instant startsAt, Instant salesOpenAt, int holdMinutes,
            int maxSeatsPerUser) {
        if (!salesOpenAt.isBefore(startsAt)) {
            throw new IllegalArgumentException("판매 시작 시각은 공연 시작 시각보다 앞서야 한다");
        }
        if (holdMinutes <= 0) {
            throw new IllegalArgumentException("선점 유지 시간은 0보다 커야 한다");
        }
        if (maxSeatsPerUser <= 0) {
            throw new IllegalArgumentException("1인 최대 좌석 수는 0보다 커야 한다");
        }
        return new Showtime(performance, startsAt, salesOpenAt, holdMinutes, maxSeatsPerUser);
    }
}
