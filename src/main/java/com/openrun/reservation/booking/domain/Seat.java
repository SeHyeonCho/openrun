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
}
